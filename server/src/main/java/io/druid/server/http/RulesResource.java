/*
 * Druid - a distributed column store.
 * Copyright 2012 - 2015 Metamarkets Group Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.druid.server.http;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import io.druid.audit.AuditInfo;
import io.druid.audit.AuditManager;
import io.druid.metadata.MetadataRuleManager;
import io.druid.server.coordinator.rules.Rule;

import org.joda.time.Interval;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import java.util.List;

/**
 */
@Path("/druid/coordinator/v1/rules")
public class RulesResource
{
  private final MetadataRuleManager databaseRuleManager;
  private final AuditManager auditManager;

  @Inject
  public RulesResource(
      MetadataRuleManager databaseRuleManager,
      AuditManager auditManager
  )
  {
    this.databaseRuleManager = databaseRuleManager;
    this.auditManager = auditManager;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getRules()
  {
    return Response.ok(databaseRuleManager.getAllRules()).build();
  }

  @GET
  @Path("/{dataSourceName}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getDatasourceRules(
      @PathParam("dataSourceName") final String dataSourceName,
      @QueryParam("full") final String full
  )
  {
    if (full != null) {
      return Response.ok(databaseRuleManager.getRulesWithDefault(dataSourceName))
                     .build();
    }
    return Response.ok(databaseRuleManager.getRules(dataSourceName))
                   .build();
  }

  // default value is used for backwards compatibility
  @POST
  @Path("/{dataSourceName}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response setDatasourceRules(
      @PathParam("dataSourceName") final String dataSourceName,
      final List<Rule> rules,
      @HeaderParam(AuditManager.X_DRUID_AUTHOR) @DefaultValue("") final String author,
      @HeaderParam(AuditManager.X_DRUID_COMMENT) @DefaultValue("") final String comment,
      @Context HttpServletRequest req
  )
  {
    if (databaseRuleManager.overrideRule(
        dataSourceName,
        rules,
        new AuditInfo(author, comment, req.getRemoteAddr())
    )) {
      return Response.ok().build();
    }
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
  }

  @GET
  @Path("/{dataSourceName}/history")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getDatasourceRuleHistory(
      @PathParam("dataSourceName") final String dataSourceName,
      @QueryParam("interval") final String interval,
      @QueryParam("count") final Integer count
  )
  {
    Interval theInterval = interval == null ? null : new Interval(interval);
    if (theInterval == null && count != null) {
      try {
        return Response.ok(auditManager.fetchAuditHistory(dataSourceName, "rules", count))
                       .build();
      }
      catch (IllegalArgumentException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                       .entity(ImmutableMap.<String, Object>of("error", e.getMessage()))
                       .build();
      }
    }
    return Response.ok(auditManager.fetchAuditHistory(dataSourceName, "rules", theInterval))
                   .build();
  }

  @GET
  @Path("/history")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getDatasourceRuleHistory(
      @QueryParam("interval") final String interval
  )
  {
    try {
      Interval theInterval = interval == null ? null : new Interval(interval);
      return Response.ok(auditManager.fetchAuditHistory("rules", theInterval))
                     .build();
    }
    catch (IllegalArgumentException e) {
      return Response.serverError().entity(ImmutableMap.<String, Object>of("error", e.getMessage())).build();
    }
  }

}
