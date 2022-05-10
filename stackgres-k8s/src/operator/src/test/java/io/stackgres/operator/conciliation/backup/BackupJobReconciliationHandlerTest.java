/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.stackgres.common.LabelFactory;
import io.stackgres.common.StringUtil;
import io.stackgres.common.crd.sgbackup.StackGresBackup;
import io.stackgres.common.resource.ResourceFinder;
import io.stackgres.common.resource.ResourceScanner;
import io.stackgres.common.resource.ResourceWriter;
import io.stackgres.testutil.JsonUtil;
import io.stackgres.testutil.StringUtils;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class BackupJobReconciliationHandlerTest {

  protected static final Logger LOGGER = LoggerFactory.getLogger(
      BackupJobReconciliationHandlerTest.class);

  @Mock
  private LabelFactory<StackGresBackup> labelFactory;

  @Mock
  private ResourceWriter<Job> jobWriter;

  @Mock
  private ResourceScanner<Pod> podScanner;

  @Mock
  private ResourceWriter<Pod> podWriter;

  @Mock
  private ResourceFinder<Job> jobFinder;

  private BackupJobReconciliationHandler handler;

  private StackGresBackup backup;

  private Job requiredJob;

  private Job deployedJob;

  private List<Pod> podList = new ArrayList<>();

  @BeforeEach
  void setUp() {
    handler = new BackupJobReconciliationHandler(
        labelFactory, jobFinder, jobWriter,
        podScanner, podWriter);
    requiredJob = JsonUtil
        .readFromJson("jobs/required.json", Job.class);

    backup = new StackGresBackup();
    backup.setMetadata(new ObjectMeta());
    backup.getMetadata().setNamespace(requiredJob.getMetadata().getNamespace());
    backup.getMetadata().setName(requiredJob.getMetadata().getName());

    deployedJob = JsonUtil
        .readFromJson("jobs/deployed.json", Job.class);
  }

  @Test
  void createResource_shouldValidateTheResourceType() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> handler.create(backup, new SecretBuilder()
            .addToData(StringUtil.generateRandom(), StringUtil.generateRandom())
            .build()));

    assertEquals("Resource must be a Job instance", ex.getMessage());

    verify(jobWriter, never()).create(any(Job.class));
    verify(podWriter, never()).update(any());
  }

  @Test
  void createResource_shouldCreateTheResource() {
    when(jobWriter.create(requiredJob)).thenReturn(requiredJob);

    HasMetadata job = handler.create(backup, requiredJob);

    assertEquals(requiredJob, job);
  }

  @Test
  void delete_shouldNotFail() {
    doNothing().when(jobWriter).delete(requiredJob);

    handler.delete(backup, requiredJob);

    verify(jobWriter, times(1)).delete(any());
  }

  @Test
  void givenJobAnnotationChanges_shouldBeAppliedDirectlyToJob() {
    setUpJob();

    final Map<String, String> requiredAnnotations = Map
        .of(StringUtils.getRandomString(), StringUtils.getRandomString(),
            "same-key", "new-value");
    requiredJob.getMetadata().setAnnotations(requiredAnnotations);
    final var deployedAnnotations = Optional.of(deployedJob).map(job -> Tuple
        .tuple(job, Map
            .of(StringUtils.getRandomString(), StringUtils.getRandomString(),
                "same-key", "old-value")))
        .map(t -> {
          t.v1.getMetadata().setAnnotations(t.v2);
          return t;
        })
        .map(Tuple2::v2)
        .orElseThrow();

    when(jobWriter.update(any())).thenReturn(requiredJob);

    handler.patch(backup, requiredJob, deployedJob);

    ArgumentCaptor<Job> jobArgumentCaptor =
        ArgumentCaptor.forClass(Job.class);
    verify(jobWriter, times(1)).update(jobArgumentCaptor.capture());
    jobArgumentCaptor.getAllValues().forEach(job -> {
      assertEquals(Seq.seq(deployedAnnotations)
          .filter(annotation -> !requiredAnnotations.containsKey(annotation.v1))
          .append(Seq.seq(requiredAnnotations))
          .collect(ImmutableMap.toImmutableMap(Tuple2::v1, Tuple2::v2)),
          job.getMetadata().getAnnotations());
    });

    verify(podWriter, times(0)).update(any());
  }

  @Test
  void givenPodAnnotationChanges_shouldBeAppliedDirectlyToPods() {
    setUpJob();

    final Map<String, String> requiredAnnotations = Map
        .of(StringUtils.getRandomString(), StringUtils.getRandomString(),
            "same-key", "new-value");
    requiredJob.getSpec().getTemplate().getMetadata().setAnnotations(requiredAnnotations);
    final var deployedAnnotations = Seq.seq(podList).map(pod -> Tuple
        .tuple(pod, Map
            .of(StringUtils.getRandomString(), StringUtils.getRandomString(),
                "same-key", "old-value")))
        .map(t -> {
          t.v1.getMetadata().setAnnotations(t.v2);
          return t;
        })
        .map(t -> t.map1(pod -> pod.getMetadata().getName()))
        .collect(ImmutableMap.toImmutableMap(Tuple2::v1, Tuple2::v2));

    when(jobWriter.update(any())).thenReturn(requiredJob);

    handler.patch(backup, requiredJob, deployedJob);

    verify(jobWriter, times(1)).update(any());

    ArgumentCaptor<Pod> podArgumentCaptor = ArgumentCaptor.forClass(Pod.class);
    verify(podWriter, times(1)).update(podArgumentCaptor.capture());
    podArgumentCaptor.getAllValues().forEach(pod -> {
      assertEquals(Seq.seq(deployedAnnotations.get(pod.getMetadata().getName()))
          .filter(annotation -> !requiredAnnotations.containsKey(annotation.v1))
          .append(Seq.seq(requiredAnnotations))
          .collect(ImmutableMap.toImmutableMap(Tuple2::v1, Tuple2::v2)),
          pod.getMetadata().getAnnotations());
    });
  }

  private void setUpJob() {
    setUpPod();

    setJobMocks(false);
  }

  private void setJobMocks(boolean returnRequiredJob) {
    lenient().when(jobFinder.findByNameAndNamespace(
        eq(requiredJob.getMetadata().getName()),
        eq(requiredJob.getMetadata().getNamespace())))
        .thenReturn(Optional.of(
            returnRequiredJob ? requiredJob : deployedJob));
    lenient().when(jobWriter.create(any())).thenReturn(requiredJob);
    lenient().when(jobWriter.update(any())).thenReturn(requiredJob);
  }

  @SuppressWarnings("unchecked")
  private void setUpPod() {
    podList.clear();
    addPod();

    lenient().when(podScanner
        .findByLabelsAndNamespace(any(), any()))
            .then(arguments -> {
              return podList
                  .stream()
                  .filter(pod -> ((Map<String, String>) arguments.getArgument(1))
                      .entrySet().stream().allMatch(label -> pod.getMetadata().getLabels()
                          .entrySet().stream().anyMatch(
                              podLabel -> podLabel.getKey().equals(label.getKey())
                              && podLabel.getValue().equals(label.getValue()))))
                  .collect(ImmutableList.toImmutableList());
            });

    lenient().doAnswer(arguments -> {
      podList.remove(arguments.getArgument(0));
      return null;
    }).when(podWriter).delete(any());
  }

  private void addPod() {
    final Map<String, String> podLabels = new HashMap<>(
        requiredJob.getSpec().getTemplate().getMetadata().getLabels());
    podList.add(new PodBuilder()
        .withNewMetadata()
        .withGenerateName(requiredJob.getMetadata().getName() + "-")
        .withNamespace(requiredJob.getMetadata().getNamespace())
        .withName(requiredJob.getMetadata().getName() + "-xxxxx")
        .withLabels(ImmutableMap.<String, String>builder()
            .putAll(podLabels)
            .build())
        .withOwnerReferences(getOwnerReferences(requiredJob))
        .endMetadata()
        .withNewSpec()
        .withNodeSelector(ImmutableMap.of())
        .endSpec()
        .build());
  }

  private ImmutableList<OwnerReference> getOwnerReferences(HasMetadata resource) {
    return ImmutableList.of(new OwnerReferenceBuilder()
        .withApiVersion(resource.getApiVersion())
        .withKind(resource.getKind())
        .withName(resource.getMetadata().getName())
        .withUid(resource.getMetadata().getUid())
        .withBlockOwnerDeletion(true)
        .withController(true)
        .build());
  }

}