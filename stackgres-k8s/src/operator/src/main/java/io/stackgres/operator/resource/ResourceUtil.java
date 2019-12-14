/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.resource;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import io.stackgres.operator.customresource.sgcluster.StackGresCluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceUtil {

  public static final String APP_KEY = "app";
  public static final String APP_NAME = "StackGres";
  public static final String CLUSTER_NAME_KEY = "cluster-name";
  public static final String CLUSTER_NAMESPACE_KEY = "cluster-namespace";
  public static final String CLUSTER_KEY = "cluster";
  public static final String DISRUPTIBLE_KEY = "disruptible";
  public static final String ROLE_KEY = "role";
  public static final String PRIMARY_ROLE = "master";
  public static final String REPLICA_ROLE = "replica";

  private static final Logger LOGGER = LoggerFactory.getLogger(ResourceUtil.class);

  private ResourceUtil() {
    throw new AssertionError("No instances for you!");
  }

  /**
   * Filter metadata of resources to find if the name match in the provided list.
   *
   * @param list resources with metadata to filter
   * @param name to check for match in the list
   * @return true if the name exists in the list
   */
  public static boolean exists(List<? extends HasMetadata> list, String name) {
    return list.stream()
        .map(HasMetadata::getMetadata)
        .map(ObjectMeta::getName)
        .anyMatch(name::equals);
  }

  /**
   * ImmutableMap of default labels used as selectors in K8s resources.
   */
  public static Map<String, String> defaultLabels() {
    return ImmutableMap.of(APP_KEY, APP_NAME);
  }

  /**
   * ImmutableMap of default labels used as selectors in K8s resources
   * outside of the namespace of the cluster.
   */
  public static Map<String, String> defaultLabels(String clusterName) {
    return ImmutableMap.of(APP_KEY, APP_NAME, CLUSTER_NAME_KEY, clusterName);
  }

  /**
   * ImmutableMap of default labels used as selectors in K8s resources.
   */
  public static Map<String, String> defaultLabels(String clusterNamespace, String clusterName) {
    return ImmutableMap.of(APP_KEY, APP_NAME, CLUSTER_NAMESPACE_KEY, clusterNamespace,
        CLUSTER_NAME_KEY, clusterName);
  }

  /**
   * ImmutableMap of default labels used as selectors in K8s pods
   * that are part of the cluster.
   */
  public static Map<String, String> statefulSetPodLabels(String clusterName) {
    return ImmutableMap.of(APP_KEY, APP_NAME, CLUSTER_NAME_KEY, clusterName,
        CLUSTER_KEY, Boolean.TRUE.toString(), DISRUPTIBLE_KEY, Boolean.TRUE.toString());
  }

  /**
   * ImmutableMap of default labels used as selectors in K8s pods
   * that are part of the cluster.
   */
  public static Map<String, String> patroniClusterLabels(String clusterName) {
    return ImmutableMap.of(APP_KEY, APP_NAME, CLUSTER_NAME_KEY, clusterName,
        CLUSTER_KEY, Boolean.TRUE.toString());
  }

  public static boolean isPrimary(Map<String, String> labels) {
    return Objects.equals(labels.get(ResourceUtil.ROLE_KEY), ResourceUtil.PRIMARY_ROLE);
  }

  public static boolean isNonDisruptiblePrimary(Map<String, String> labels) {
    return isPrimary(labels)
        && Objects.equals(labels.get(ResourceUtil.DISRUPTIBLE_KEY), Boolean.FALSE.toString());
  }

  public static String getNameWithIndexPattern(String name) {
    return "^" + Pattern.quote(name + "-") + "([0-9]+)$";
  }

  /**
   * Extract the index of a StatefulSet's pod.
   */
  public static Integer extractPodIndex(StackGresCluster cluster, ObjectMeta podMetadata) {
    Matcher matcher = Pattern.compile(getNameWithIndexPattern(cluster.getMetadata().getName()))
        .matcher(podMetadata.getName());
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(1));
    }
    throw new IllegalStateException("Can not extract index from pod "
        + podMetadata.getNamespace() + "." + podMetadata.getName() + " for cluster "
        + cluster.getMetadata().getNamespace() + "." + cluster.getMetadata().getName());
  }

  /**
   * Get a custom resource definition from Kubernetes.
   *
   * @param client Kubernetes client to call the API.
   * @param crdName Name of the CDR to lookup.
   * @return the CustomResourceDefinition model.
   */
  public static Optional<CustomResourceDefinition> getCustomResource(KubernetesClient client,
      String crdName) {
    return Optional.ofNullable(client.customResourceDefinitions().withName(crdName).get());
  }

  /**
   * Log in debug the YAML of kubernetes resources passed as argument.
   *
   * @param resource KubernetesResource that has metadata
   */
  public static void logAsYaml(HasMetadata resource) {
    try {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("{}: {}", resource.getClass().getSimpleName(),
            SerializationUtils.dumpWithoutRuntimeStateAsYaml(resource));
      }
    } catch (JsonProcessingException e) {
      LOGGER.debug("Error dump as Yaml:", e);
    }
  }

  /**
   * Get the object reference of any resource.
   */
  public static ObjectReference getObjectReference(HasMetadata resource) {
    return new ObjectReferenceBuilder()
        .withApiVersion(resource.getApiVersion())
        .withKind(resource.getKind())
        .withNamespace(resource.getMetadata().getNamespace())
        .withName(resource.getMetadata().getName())
        .withResourceVersion(resource.getMetadata().getResourceVersion())
        .withUid(resource.getMetadata().getUid())
        .build();
  }

  /**
   * Get the owner reference of any resource.
   */
  public static OwnerReference getOwnerReference(HasMetadata resource) {
    return new OwnerReferenceBuilder()
        .withApiVersion(resource.getApiVersion())
        .withKind(resource.getKind())
        .withName(resource.getMetadata().getName())
        .withUid(resource.getMetadata().getUid())
        .withController(true)
        .build();
  }

}