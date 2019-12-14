/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.cluster;

import io.stackgres.operator.common.StackgresClusterReview;
import io.stackgres.operatorframework.Validator;

public interface ClusterValidator extends Validator<StackgresClusterReview> {
}