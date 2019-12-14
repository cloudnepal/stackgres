/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.controller;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.stackgres.operator.app.KubernetesClientFactory;
import io.stackgres.operator.app.ObjectMapperProvider;
import io.stackgres.operator.common.SidecarEntry;
import io.stackgres.operator.common.StackGresClusterConfig;
import io.stackgres.operator.common.StackGresSidecarTransformer;
import io.stackgres.operator.customresource.sgbackupconfig.StackGresBackupConfig;
import io.stackgres.operator.customresource.sgbackupconfig.StackGresBackupConfigDefinition;
import io.stackgres.operator.customresource.sgbackupconfig.StackGresBackupConfigDoneable;
import io.stackgres.operator.customresource.sgbackupconfig.StackGresBackupConfigList;
import io.stackgres.operator.customresource.sgcluster.StackGresCluster;
import io.stackgres.operator.customresource.sgcluster.StackGresClusterDefinition;
import io.stackgres.operator.customresource.sgcluster.StackGresClusterDoneable;
import io.stackgres.operator.customresource.sgcluster.StackGresClusterList;
import io.stackgres.operator.customresource.sgpgconfig.StackGresPostgresConfig;
import io.stackgres.operator.customresource.sgpgconfig.StackGresPostgresConfigDefinition;
import io.stackgres.operator.customresource.sgpgconfig.StackGresPostgresConfigDoneable;
import io.stackgres.operator.customresource.sgpgconfig.StackGresPostgresConfigList;
import io.stackgres.operator.customresource.sgprofile.StackGresProfile;
import io.stackgres.operator.customresource.sgprofile.StackGresProfileDefinition;
import io.stackgres.operator.customresource.sgprofile.StackGresProfileDoneable;
import io.stackgres.operator.customresource.sgprofile.StackGresProfileList;
import io.stackgres.operator.patroni.Patroni;
import io.stackgres.operator.resource.ResourceHandlerSelector;
import io.stackgres.operator.resource.ResourceUtil;
import io.stackgres.operator.resource.SidecarFinder;
import io.stackgres.operator.sidecars.envoy.Envoy;

import org.jooq.lambda.Unchecked;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ClusterReconciliationCycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterReconciliationCycle.class);

  private final KubernetesClientFactory kubClientFactory;
  private final SidecarFinder sidecarFinder;
  private final Patroni patroni;
  private final ResourceHandlerSelector handlerSelector;
  private final ClusterStatusManager statusManager;
  private final EventController eventController;
  private final ExecutorService executorService = Executors.newSingleThreadExecutor(
      r -> new Thread(r, "ReconciliationCycle"));
  private final ArrayBlockingQueue<Boolean> arrayBlockingQueue = new ArrayBlockingQueue<>(1);
  private final ObjectMapper objectMapper;

  private final CompletableFuture<Void> stopped = new CompletableFuture<>();
  private boolean close = false;

  private AtomicInteger reconciliationCount = new AtomicInteger(0);

  /**
   * Create a {@code ClusterReconciliationCycle} instance.
   */
  @Inject
  public ClusterReconciliationCycle(KubernetesClientFactory kubClientFactory,
      SidecarFinder sidecarFinder, Patroni patroni, ResourceHandlerSelector handlerSelector,
      ClusterStatusManager statusManager, EventController eventController,
      ObjectMapperProvider objectMapperProvider) {
    super();
    this.kubClientFactory = kubClientFactory;
    this.sidecarFinder = sidecarFinder;
    this.patroni = patroni;
    this.handlerSelector = handlerSelector;
    this.statusManager = statusManager;
    this.eventController = eventController;
    this.objectMapper = objectMapperProvider.objectMapper();
  }

  void onStart(@Observes StartupEvent ev) {
    executorService.execute(this::reconciliationCycleLoop);
  }

  void onStop(@Observes ShutdownEvent ev) throws Exception {
    close = true;
    reconcile();
    executorService.shutdown();
    arrayBlockingQueue.offer(true);
    stopped.join();
  }

  public void reconcile() {
    arrayBlockingQueue.offer(true);
  }

  private void reconciliationCycleLoop() {
    LOGGER.info("Cluster reconciliation cycle loop started");
    while (true) {
      try {
        arrayBlockingQueue.take();
        if (close) {
          break;
        }
        reconciliationCycle();
      } catch (Exception ex) {
        LOGGER.error("Reconciliation cycle loop was interrupted", ex);
      }
    }
    LOGGER.info("Cluster reconciliation cycle loop stopped");
    stopped.complete(null);
  }

  private void reconciliationCycle() {
    final int cycleId = reconciliationCount.incrementAndGet();
    String cycleName = "Reconciliation Cycle " + cycleId;

    LOGGER.trace("Starting " + cycleName);
    try (KubernetesClient client = kubClientFactory.create()) {
      LOGGER.trace(cycleName + " getting existing clusters");
      ImmutableList<StackGresClusterConfig> existingClusters = getExistingClusters(client);
      LOGGER.trace(cycleName + " deleting orphan resources");
      deleteOrphanResources(client, existingClusters);

      for (StackGresClusterConfig clusterConfig : existingClusters) {
        StackGresCluster cluster = clusterConfig.getCluster();

        String clusterId = cluster.getMetadata().getNamespace() + "."
            + cluster.getMetadata().getName();

        try {
          LOGGER.trace(cycleName + " reconciling cluster " + cluster.getMetadata().getName());
          ImmutableList<HasMetadata> existingResourcesOnly = getExistingResources(
              client, clusterConfig);
          ImmutableList<HasMetadata> requiredResourcesOnly = patroni.getResources(
              ImmutableResourceGeneratorContext.builder()
              .clusterConfig(clusterConfig)
              .addAllExistingResources(existingResourcesOnly)
              .build());
          ImmutableList<Tuple2<HasMetadata, Optional<HasMetadata>>> existingResources =
              existingResourcesOnly
              .stream()
              .map(existingResource -> Tuple.tuple(existingResource,
                  findResourceIn(existingResource, requiredResourcesOnly)))
              .collect(ImmutableList.toImmutableList());
          ImmutableList<Tuple2<HasMetadata, Optional<HasMetadata>>> requiredResources =
              requiredResourcesOnly
              .stream()
              .map(requiredResource -> Tuple.tuple(requiredResource,
                  Optional.of(findResourceIn(requiredResource, existingResourcesOnly))
                  .filter(Optional::isPresent)
                  .orElseGet(() -> handlerSelector.find(client, clusterConfig, requiredResource))))
              .collect(ImmutableList.toImmutableList());
          ClusterReconciliator.builder()
            .withEventController(eventController)
            .withHandlerSelector(handlerSelector)
            .withStatusManager(statusManager)
            .withClient(client)
            .withObjectMapper(objectMapper)
            .withClusterConfig(clusterConfig)
            .withRequiredResources(requiredResources)
            .withExistingResources(existingResources)
            .build()
            .reconcile();
        } catch (Exception ex) {
          LOGGER.error(cycleName + " failed reconciling StackGres cluster " + clusterId, ex);
          eventController.sendEvent(EventReason.CLUSTER_CONFIG_ERROR,
              "StackGres Cluster " + clusterId + " reconciliation failed: "
                  + ex.getMessage(), cluster);
        }
      }
      LOGGER.trace(cycleName + " ended successfully");
    } catch (Exception ex) {
      LOGGER.error("Cluster reconciliation cycle failed", ex);
      eventController.sendEvent(EventReason.CLUSTER_CONFIG_ERROR,
          "StackGres Cluster reconciliation cycle failed: "
              + ex.getMessage(), null);
    }
  }

  private Optional<HasMetadata> findResourceIn(HasMetadata resource,
      ImmutableList<HasMetadata> resources) {
    return resources
        .stream()
        .filter(otherResource -> resource.getKind()
            .equals(otherResource.getKind()))
        .filter(otherResource -> Objects.equals(resource.getMetadata().getNamespace(),
            otherResource.getMetadata().getNamespace()))
        .filter(otherResource -> resource.getMetadata().getName()
            .equals(otherResource.getMetadata().getName()))
        .findAny();
  }

  private void deleteOrphanResources(KubernetesClient client,
      ImmutableList<StackGresClusterConfig> existingClusters) {
    ImmutableList<HasMetadata> existingOrphanResources = getExistingOrphanResources(
        client, existingClusters);
    Set<Tuple2<String, String>> deletedClusters = new HashSet<>();
    for (HasMetadata existingOrphanResource : existingOrphanResources) {
      LOGGER.debug("Deleteing resource {} of type {}"
          + " since does not belong to any cluster",
          getResourceIdentifier(existingOrphanResource),
          existingOrphanResource.getKind());
      handlerSelector.delete(client, null, existingOrphanResource);
      deletedClusters.add(Tuple.tuple(
          existingOrphanResource.getMetadata().getNamespace(),
          existingOrphanResource.getMetadata().getLabels().get(ResourceUtil.CLUSTER_NAME_KEY)));
    }

    for (Tuple2<String, String> deletedCluster : deletedClusters) {
      LOGGER.info("Cluster deleted: '{}'",
          getResourceIdentifier(deletedCluster.v1, deletedCluster.v2));
      eventController.sendEvent(EventReason.CLUSTER_DELETED,
          "StackGres Cluster " + deletedCluster.v1 + "."
              + deletedCluster.v2 + " deleted");
    }
  }

  private StackGresClusterConfig getClusterConfig(StackGresCluster cluster,
      KubernetesClient client) {
    return StackGresClusterConfig.builder()
        .withCluster(cluster)
        .withProfile(getProfile(cluster, client))
        .withPostgresConfig(getPostgresConfig(cluster, client))
        .withBackupConfig(getBackupConfig(cluster, client))
        .withSidecars(Stream.of(
            Stream.of(Optional.of(Envoy.NAME)
            .filter(envoy -> !cluster.getSpec().getSidecars().contains(envoy)))
            .filter(Optional::isPresent)
            .map(Optional::get),
            cluster.getSpec().getSidecars().stream())
            .flatMap(s -> s)
            .map(sidecar -> sidecarFinder.getSidecarTransformer(sidecar))
            .map(Unchecked.function(sidecar -> getSidecarEntry(cluster, client, sidecar)))
            .collect(ImmutableList.toImmutableList()))
        .build();
  }

  private <T extends CustomResource> SidecarEntry<T> getSidecarEntry(StackGresCluster cluster,
      KubernetesClient client, StackGresSidecarTransformer<T> sidecar) throws Exception {
    Optional<T> sidecarConfig = sidecar.getConfig(cluster, client);
    return new SidecarEntry<T>(sidecar, sidecarConfig);
  }

  private ImmutableList<StackGresClusterConfig> getExistingClusters(KubernetesClient client) {
    return ResourceUtil.getCustomResource(client, StackGresClusterDefinition.NAME)
      .map(crd -> client
          .customResources(crd,
              StackGresCluster.class,
              StackGresClusterList.class,
              StackGresClusterDoneable.class)
          .inAnyNamespace()
          .list()
          .getItems()
          .stream()
          .map(cluster -> getClusterConfig(cluster, client))
          .collect(ImmutableList.toImmutableList()))
      .orElseThrow(() -> new IllegalStateException("StackGres is not correctly installed:"
          + " CRD " + StackGresClusterDefinition.NAME + " not found."));
  }

  private ImmutableList<HasMetadata> getExistingOrphanResources(KubernetesClient client,
      ImmutableList<StackGresClusterConfig> existingClusters) {
    return ImmutableList.<HasMetadata>builder()
        .addAll(handlerSelector.getOrphanResources(client, existingClusters)
            .iterator())
        .build()
        .stream()
        .collect(ImmutableList.toImmutableList());
  }

  private ImmutableList<HasMetadata> getExistingResources(KubernetesClient client,
      StackGresClusterConfig cluster) {
    return ImmutableList.<HasMetadata>builder()
        .addAll(handlerSelector.getResources(client, cluster)
            .iterator())
        .build()
        .stream()
        .collect(ImmutableList.toImmutableList());
  }

  private Optional<StackGresPostgresConfig> getPostgresConfig(StackGresCluster cluster,
      KubernetesClient client) {
    final String namespace = cluster.getMetadata().getNamespace();
    final String pgConfig = cluster.getSpec().getPostgresConfig();
    if (pgConfig != null) {
      Optional<CustomResourceDefinition> crd =
          ResourceUtil.getCustomResource(client, StackGresPostgresConfigDefinition.NAME);
      if (crd.isPresent()) {
        return Optional.ofNullable(client
            .customResources(crd.get(),
                StackGresPostgresConfig.class,
                StackGresPostgresConfigList.class,
                StackGresPostgresConfigDoneable.class)
            .inNamespace(namespace)
            .withName(pgConfig)
            .get());
      }
    }
    return Optional.empty();
  }

  private Optional<StackGresBackupConfig> getBackupConfig(StackGresCluster cluster,
      KubernetesClient client) {
    final String namespace = cluster.getMetadata().getNamespace();
    final String backupConfig = cluster.getSpec().getBackupConfig();
    if (backupConfig != null) {
      Optional<CustomResourceDefinition> crd =
          ResourceUtil.getCustomResource(client, StackGresBackupConfigDefinition.NAME);
      if (crd.isPresent()) {
        return Optional.ofNullable(client
            .customResources(crd.get(),
                StackGresBackupConfig.class,
                StackGresBackupConfigList.class,
                StackGresBackupConfigDoneable.class)
            .inNamespace(namespace)
            .withName(backupConfig)
            .get());
      }
    }
    return Optional.empty();
  }

  private Optional<StackGresProfile> getProfile(StackGresCluster cluster,
      KubernetesClient client) {
    final String namespace = cluster.getMetadata().getNamespace();
    final String profileName = cluster.getSpec().getResourceProfile();
    if (profileName != null) {
      Optional<CustomResourceDefinition> crd =
          ResourceUtil.getCustomResource(client, StackGresProfileDefinition.NAME);
      if (crd.isPresent()) {
        return Optional.ofNullable(client
            .customResources(crd.get(),
                StackGresProfile.class,
                StackGresProfileList.class,
                StackGresProfileDoneable.class)
            .inNamespace(namespace)
            .withName(profileName)
            .get());
      }
    }
    return Optional.empty();
  }

  private String getResourceIdentifier(HasMetadata resource) {
    return getResourceIdentifier(resource.getMetadata().getNamespace(),
        resource.getMetadata().getName());
  }

  private String getResourceIdentifier(String namespace, String name) {
    if (namespace == null) {
      return name;
    }
    return namespace + "." + name;
  }

}