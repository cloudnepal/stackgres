/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.mutation;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonpatch.AddOperation;
import com.github.fge.jsonpatch.JsonPatchOperation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.client.CustomResource;
import io.stackgres.operator.common.ConfigContext;
import io.stackgres.operator.common.ConfigProperty;
import io.stackgres.operatorframework.AdmissionReview;
import io.stackgres.operatorframework.JsonPatchMutator;

public interface DefaultAnnotationMutator<R extends CustomResource, T extends AdmissionReview<R>>
    extends JsonPatchMutator<T> {

  JsonPointer ANNOTATION_POINTER = JsonPointer.of("metadata", "annotations");

  String STACKGRES_PREFIX = "stackgres.io/";

  default List<JsonPatchOperation> getAnnotationsToAdd(R resouce, ConfigContext configContext) {

    Optional<Map<String, String>> crAnnotations = Optional
        .ofNullable(resouce.getMetadata().getAnnotations());

    Map<String, String> givenAnnotations = crAnnotations.orElseGet(IdentityHashMap::new);

    List<String> existentAnnotations = givenAnnotations.keySet()
        .stream()
        .filter(k -> k.startsWith(STACKGRES_PREFIX))
        .map(k -> k.substring(STACKGRES_PREFIX.length()))
        .collect(Collectors.toList());

    Map<String, String> defaultAnnotations = getDefaultAnnotationValues(configContext);

    Map<String, String> annotationsToAdd = defaultAnnotations.entrySet().stream()
        .filter(e -> !existentAnnotations.contains(e.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    ImmutableList.Builder<JsonPatchOperation> operations = ImmutableList.builder();

    if (!crAnnotations.isPresent()) {
      operations.add(new AddOperation(ANNOTATION_POINTER, FACTORY.objectNode()));
    }

    operations.addAll(buildAnnotations(annotationsToAdd));

    return operations.build();
  }

  default List<JsonPatchOperation> buildAnnotations(Map<String, String> annotations) {

    return annotations.entrySet().stream()
        .map(entry -> new AddOperation(
            ANNOTATION_POINTER.append(STACKGRES_PREFIX + entry.getKey()),
            FACTORY.textNode(entry.getValue())
        )).collect(ImmutableList.toImmutableList());

  }

  default Map<String, String> getDefaultAnnotationValues(ConfigContext configContext) {

    String operatorVersion = configContext.getProperty(ConfigProperty.OPERATOR_VERSION)
        .orElseThrow(() -> new IllegalStateException("Operator version not configured"));

    String operatorVersionKey = ConfigProperty.OPERATOR_VERSION.systemProperty().split("\\.")[1];

    return ImmutableMap.<String, String>builder()
        .put(operatorVersionKey, operatorVersion)
        .build();
  }
}