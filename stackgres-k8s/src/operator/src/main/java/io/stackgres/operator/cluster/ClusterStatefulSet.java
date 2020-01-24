/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.cluster;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapEnvSourceBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorRequirementBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpecBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.PodAntiAffinityBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.api.model.TCPSocketActionBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategyBuilder;
import io.stackgres.operator.common.StackGresClusterContext;
import io.stackgres.operator.common.StackGresComponents;
import io.stackgres.operator.common.StackGresUtil;
import io.stackgres.operator.configuration.ImmutableStorageConfig;
import io.stackgres.operator.configuration.StorageConfig;
import io.stackgres.operator.controller.ResourceGeneratorContext;
import io.stackgres.operator.patroni.PatroniConfigMap;
import io.stackgres.operator.patroni.PatroniRole;
import io.stackgres.operator.patroni.StatefulsetResourceBuilder;
import io.stackgres.operator.resource.ResourceUtil;
import io.stackgres.operator.sidecars.envoy.Envoy;
import io.stackgres.operatorframework.factories.EnvironmentVariablesFactory;
import io.stackgres.operatorframework.factories.InitContainerFactory;
import io.stackgres.operatorframework.factories.ResourceRequirementsFactory;
import io.stackgres.operatorframework.factories.VolumeMountsFactory;
import io.stackgres.operatorframework.factories.VolumesFactory;
import org.jooq.lambda.Seq;
import org.jooq.lambda.Unchecked;

@ApplicationScoped
public class ClusterStatefulSet implements StatefulsetResourceBuilder {

  public static final String PATRONI_CONTAINER_NAME = "patroni";
  public static final String DATA_SUFFIX = "-data";
  public static final String BACKUP_SUFFIX = "-backup";
  public static final String SOCKET_VOLUME_NAME = "socket";
  public static final String PG_VOLUME_PATH = "/var/lib/postgresql";
  public static final String DATA_VOLUME_PATH = PG_VOLUME_PATH + "/data";
  public static final String BACKUP_VOLUME_PATH = PG_VOLUME_PATH + "/backups";
  public static final String GCS_CREDENTIALS_VOLUME_NAME = "gcs-credentials";
  public static final String GCS_CREDENTIALS_FILE_NAME = "google-service-account-key.json";
  public static final String GCS_CONFIG_PATH = "/.gcs";
  public static final String GCS_RESTORE_CREDENTIALS_VOLUME_NAME = "gcs-restore-credentials";
  public static final String GCS_RESTORE_CREDENTIALS_FILE_NAME =
      "restore-google-service-account-key.json";
  public static final String GCS_RESTORE_CONFIG_PATH = "/restore/.gcs";
  public static final String WAL_G_WRAPPER_VOLUME_NAME = "wal-g-wrapper";
  public static final String WAL_G_RESTORE_WRAPPER_VOLUME_NAME = "wal-g-restore-wrapper";
  public static final String RESTORE_ENTRYPOINT_VOLUME = "restore-entrypoint";
  public static final String RESTORE_ENTRYPOINT_VOLUME_PATH = "/etc/patroni/restore";

  private static final String IMAGE_PREFIX = "docker.io/ongres/patroni:v%s-pg%s-build-%s";
  private static final String PATRONI_VERSION = StackGresComponents.get("patroni");

  @Inject
  ResourceRequirementsFactory<StackGresClusterContext> resourceRequirementsFactory;

  @Inject
  EnvironmentVariablesFactory<StackGresClusterContext> environmentVariablesFactory;

  @Inject
  InitContainerFactory<StackGresClusterContext> initContainerFactory;

  @Inject
  VolumesFactory<StackGresClusterContext> volumesFactory;

  @Inject
  VolumeMountsFactory<StackGresClusterContext> volumeMountsFactory;

  public static String dataName(StackGresClusterContext clusterContext) {
    String name = clusterContext.getCluster().getMetadata().getName();
    return ResourceUtil.resourceName(name + ClusterStatefulSet.DATA_SUFFIX);
  }

  public static String backupName(StackGresClusterContext clusterContext) {
    String name = clusterContext.getCluster().getMetadata().getName();
    return ResourceUtil.resourceName(name + ClusterStatefulSet.BACKUP_SUFFIX);
  }

  /**
   * Create a new StatefulSet based on the StackGresCluster definition.
   */
  public List<HasMetadata> create(
      ResourceGeneratorContext<StackGresClusterContext> context) {
    StackGresClusterContext clusterContext = context.getContext();

    final String name = clusterContext.getCluster().getMetadata().getName();
    final String namespace = clusterContext.getCluster().getMetadata().getNamespace();
    final String pgVersion = StackGresComponents.calculatePostgresVersion(
        clusterContext.getCluster().getSpec().getPostgresVersion());

    ResourceRequirements podResources = resourceRequirementsFactory
        .getPodRequirements(clusterContext);

    ImmutableList<EnvVar> statefulSetEnvVariables = environmentVariablesFactory
        .getEnvironmentVariables(clusterContext);

    StorageConfig dataStorageConfig = ImmutableStorageConfig.builder()
        .size(clusterContext.getCluster().getSpec().getVolumeSize())
        .storageClass(Optional.ofNullable(
            clusterContext.getCluster().getSpec().getStorageClass())
            .orElse(null))
        .build();

    final PersistentVolumeClaimSpecBuilder volumeClaimSpec = new PersistentVolumeClaimSpecBuilder()
        .withAccessModes("ReadWriteOnce")
        .withResources(dataStorageConfig.getResourceRequirements())
        .withStorageClassName(dataStorageConfig.getStorageClass());

    final Map<String, String> labels = ResourceUtil.clusterLabels(clusterContext.getCluster());
    final Map<String, String> podLabels = ResourceUtil.statefulSetPodLabels(
        clusterContext.getCluster());

    StatefulSet clusterStatefulSet = new StatefulSetBuilder()
        .withNewMetadata()
        .withNamespace(namespace)
        .withName(name)
        .withLabels(labels)
        .withOwnerReferences(ImmutableList.of(ResourceUtil.getOwnerReference(
            clusterContext.getCluster())))
        .endMetadata()
        .withNewSpec()
        .withReplicas(clusterContext.getCluster().getSpec().getInstances())
        .withSelector(new LabelSelectorBuilder()
            .addToMatchLabels(podLabels)
            .build())
        .withUpdateStrategy(new StatefulSetUpdateStrategyBuilder()
            .withType("OnDelete")
            .build())
        .withServiceName(name)
        .withTemplate(new PodTemplateSpecBuilder()
            .withMetadata(new ObjectMetaBuilder()
                .addToLabels(podLabels)
                .build())
            .withNewSpec()
            .withAffinity(Optional.of(new AffinityBuilder()
                .withPodAntiAffinity(new PodAntiAffinityBuilder()
                    .addAllToRequiredDuringSchedulingIgnoredDuringExecution(ImmutableList.of(
                        new PodAffinityTermBuilder()
                            .withLabelSelector(new LabelSelectorBuilder()
                                .withMatchExpressions(new LabelSelectorRequirementBuilder()
                                        .withKey(ResourceUtil.APP_KEY)
                                        .withOperator("In")
                                        .withValues(ResourceUtil.APP_NAME)
                                        .build(),
                                    new LabelSelectorRequirementBuilder()
                                        .withKey("cluster")
                                        .withOperator("In")
                                        .withValues("true")
                                        .build())
                                .build())
                            .withTopologyKey("kubernetes.io/hostname")
                            .build()))
                    .build())
                .build())
                .filter(affinity -> Optional.ofNullable(
                    clusterContext.getCluster().getSpec().getNonProduction())
                    .map(nonProduction -> nonProduction.getDisableClusterPodAntiAffinity())
                    .map(disableClusterPodAntiAffinity -> !disableClusterPodAntiAffinity)
                    .orElse(true))
                .orElse(null))
            .withShareProcessNamespace(Boolean.TRUE)
            .withServiceAccountName(PatroniRole.roleName(clusterContext))
            .addNewContainer()
            .withName(PATRONI_CONTAINER_NAME)
            .withImage(String.format(IMAGE_PREFIX,
                PATRONI_VERSION, pgVersion, StackGresUtil.CONTAINER_BUILD))
            .withCommand("/bin/sh", "-exc", Unchecked.supplier(() -> Resources
                .asCharSource(ClusterStatefulSet.class.getResource("/start-patroni.sh"),
                    StandardCharsets.UTF_8)
                .read()).get())
            .withImagePullPolicy("Always")
            .withSecurityContext(new SecurityContextBuilder()
                .withRunAsUser(999L)
                .withAllowPrivilegeEscalation(Boolean.FALSE)
                .build())
            .withPorts(
                new ContainerPortBuilder()
                    .withName(PatroniConfigMap.POSTGRES_PORT_NAME)
                    .withContainerPort(Envoy.PG_ENTRY_PORT).build(),
                new ContainerPortBuilder()
                    .withName(PatroniConfigMap.POSTGRES_REPLICATION_PORT_NAME)
                    .withContainerPort(Envoy.PG_RAW_ENTRY_PORT).build(),
                new ContainerPortBuilder().withContainerPort(8008).build())
            .withVolumeMounts(volumeMountsFactory.getVolumeMounts(clusterContext))
            .withEnvFrom(new EnvFromSourceBuilder()
                .withConfigMapRef(new ConfigMapEnvSourceBuilder()
                    .withName(name).build())
                .build())
            .withEnv(statefulSetEnvVariables)
            .withLivenessProbe(new ProbeBuilder()
                .withTcpSocket(new TCPSocketActionBuilder()
                    .withPort(new IntOrString(5432))
                    .build())
                .withInitialDelaySeconds(15)
                .withPeriodSeconds(20)
                .withFailureThreshold(6)
                .build())
            .withReadinessProbe(new ProbeBuilder()
                .withHttpGet(new HTTPGetActionBuilder()
                    .withPath("/health")
                    .withPort(new IntOrString(8008))
                    .withScheme("HTTP")
                    .build())
                .withInitialDelaySeconds(5)
                .withPeriodSeconds(10)
                .build())
            .withResources(podResources)
            .endContainer()
            .withVolumes(volumesFactory.getVolumes(clusterContext))
            .withTerminationGracePeriodSeconds(60L)
            .withInitContainers(initContainerFactory.getInitContainers(clusterContext))
            .addAllToContainers(clusterContext.getSidecars().stream()
                .map(sidecarEntry -> sidecarEntry.getSidecar().getContainer(context))
                .collect(ImmutableList.toImmutableList()))
            .addAllToVolumes(clusterContext.getSidecars().stream()
                .flatMap(sidecarEntry -> sidecarEntry.getSidecar().getVolumes(context).stream())
                .collect(ImmutableList.toImmutableList()))
            .endSpec()
            .build())
        .withVolumeClaimTemplates(Stream.of(
            Stream.of(new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(dataName(clusterContext))
                .withLabels(labels)
                .endMetadata()
                .withSpec(volumeClaimSpec.build())
                .build()))
            .flatMap(s -> s)
            .toArray(PersistentVolumeClaim[]::new))
        .endSpec()
        .build();

    return ImmutableList.<HasMetadata>builder()
        .addAll(clusterContext.getSidecars().stream()
            .flatMap(sidecarEntry -> sidecarEntry.getSidecar().getResources(context).stream())
            .iterator())
        .addAll(Seq.seq(context.getExistingResources())
            .filter(existingResource -> existingResource instanceof Pod)
            .map(HasMetadata::getMetadata)
            .filter(existingPodMetadata -> Objects.equals(
                existingPodMetadata.getLabels().get(ResourceUtil.CLUSTER_KEY),
                Boolean.TRUE.toString()))
            .map(existingPodMetadata -> new PodBuilder()
                .withNewMetadata()
                .withNamespace(existingPodMetadata.getNamespace())
                .withName(existingPodMetadata.getName())
                .withLabels(podLabels)
                .endMetadata()
                .build()))
        .add(clusterStatefulSet)
        .build();
  }

}