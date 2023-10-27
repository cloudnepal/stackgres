/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.rest;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.Path;

import io.quarkus.security.Authenticated;
import io.stackgres.apiweb.dto.shardeddbops.ShardedDbOpsDto;
import io.stackgres.common.crd.sgshardeddbops.StackGresShardedDbOps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@Path("namespaces/{namespace:[a-z0-9]([-a-z0-9]*[a-z0-9])?}/sgshardeddbops")
@RequestScoped
@Authenticated
public class NamespacedShardedDbOpsResource
    extends AbstractNamespacedRestService<ShardedDbOpsDto, StackGresShardedDbOps> {

  @Operation(
      responses = {
          @ApiResponse(responseCode = "200", description = "OK",
              content = {@Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = ShardedDbOpsDto.class))})
      })
  @Override
  public ShardedDbOpsDto get(String namespace, String name) {
    return super.get(namespace, name);
  }

}