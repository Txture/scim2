/*
 * Copyright 2015 UnboundID Corp.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPLv2 only)
 * or the terms of the GNU Lesser General Public License (LGPLv2.1 only)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 */

package com.unboundid.scim2.client.requests;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unboundid.scim2.client.ScimService;
import com.unboundid.scim2.client.SearchResultHandler;
import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.messages.SearchRequest;
import com.unboundid.scim2.common.messages.SortOrder;
import com.unboundid.scim2.common.utils.ApiConstants;
import com.unboundid.scim2.common.utils.AttributeSet;
import com.unboundid.scim2.common.utils.SchemaUtils;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;

import static com.unboundid.scim2.client.ScimService.MEDIA_TYPE_SCIM_TYPE;
import static com.unboundid.scim2.common.utils.ApiConstants.*;

/**
 * A builder for SCIM search requests.
 */
public final class SearchRequestBuilder
    extends ResourceReturningRequestBuilder<SearchRequestBuilder>
{
  private String filter;
  private String sortBy;
  private SortOrder sortOrder;
  private Integer startIndex;
  private Integer count;

  /**
   * Create a new  search request builder.
   *
   * @param target The WebTarget to search.
   */
  public SearchRequestBuilder(final WebTarget target)
  {
    super(target);
  }

  /**
   * Request filtering of resources.
   *
   * @param filter the filter string used to request a subset of resources.
   * @return This builder.
   */
  public SearchRequestBuilder filter(final String filter)
  {
    this.filter = filter;
    return this;
  }

  /**
   * Request sorting of resources.
   *
   * @param sortBy the string indicating the attribute whose value shall be used
   *               to order the returned responses.
   * @param sortOrder the order in which the sortBy parameter is applied.
   * @return This builder.
   */
  public SearchRequestBuilder sort(final String sortBy,
                                   final SortOrder sortOrder)
  {
    this.sortBy = sortBy;
    this.sortOrder = sortOrder;
    return this;
  }

  /**
   * Request pagination of resources.
   *
   * @param startIndex the 1-based index of the first query result.
   * @param count the desired maximum number of query results per page.
   * @return This builder.
   */
  public SearchRequestBuilder page(final int startIndex,
                                   final int count)
  {
    this.startIndex = startIndex;
    this.count = count;
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  WebTarget buildTarget()
  {
    WebTarget target = super.buildTarget();
    if(filter != null)
    {
      target = target.queryParam(QUERY_PARAMETER_FILTER, filter);
    }
    if(sortBy != null && sortOrder != null)
    {
      target = target.queryParam(QUERY_PARAMETER_SORT_BY, sortBy);
      target = target.queryParam(QUERY_PARAMETER_SORT_ORDER,
          sortOrder.getName());
    }
    if(startIndex != null && count != null)
    {
      target = target.queryParam(QUERY_PARAMETER_PAGE_START_INDEX, startIndex);
      target = target.queryParam(QUERY_PARAMETER_PAGE_SIZE, count);
    }
    return target;
  }

  /**
   * Invoke the SCIM retrieve request using GET.
   *
   * @param <T> The type of objects to return.
   * @param cls The Java class object used to determine the type to return.
   * @return The ListResponse containing the search results.
   * @throws ScimException If an error occurred.
   */
  public <T> ListResponse<T> invoke(final Class<T> cls)
      throws ScimException
  {
    ListResponseBuilder<T> listResponseBuilder = new ListResponseBuilder<T>();
    invoke(false, listResponseBuilder, cls);
    return listResponseBuilder.build();
  }

  /**
   * Invoke the SCIM retrieve request using GET.
   *
   * @param <T> The type of objects to return.
   * @param resultHandler The search result handler that should be used to
   *                      process the resources.
   * @param cls The Java class object used to determine the type to return.
   * @throws ScimException If an error occurred.
   */
  public <T> void invoke(
      final SearchResultHandler<T> resultHandler,
      final Class<T> cls) throws ScimException
  {
    invoke(false, resultHandler, cls);
  }

  /**
   * Invoke the SCIM retrieve request using POST.
   *
   * @param <T> The type of objects to return.
   * @param cls The Java class object used to determine the type to return.
   * @return The ListResponse containing the search results.
   * @throws ScimException If an error occurred.
   */
  public <T extends ScimResource> ListResponse<T> invokePost(final Class<T> cls)
      throws ScimException
  {
    ListResponseBuilder<T> listResponseBuilder = new ListResponseBuilder<T>();
    invoke(true, listResponseBuilder, cls);
    return listResponseBuilder.build();
  }

  /**
   * Invoke the SCIM retrieve request using POST.
   *
   * @param <T> The type of objects to return.
   * @param resultHandler The search result handler that should be used to
   *                      process the resources.
   * @param cls The Java class object used to determine the type to return.
   * @throws ScimException If an error occurred.
   */
  public <T> void invokePost(
      final SearchResultHandler<T> resultHandler, final Class<T> cls)
      throws ScimException
  {
    invoke(true, resultHandler, cls);
  }

  /**
   * Invoke the SCIM retrieve request.
   *
   * @param post {@code true} to send the request using POST or {@code false}
   *             to send the request using GET.
   * @param <T> The type of objects to return.
   * @param resultHandler The search result handler that should be used to
   *                      process the resources.
   * @param cls The Java class object used to determine the type to return.
   * @throws ScimException If an error occurred.
   */
  private <T> void invoke(
      final boolean post, final SearchResultHandler<T> resultHandler,
      final Class<T> cls)
      throws ScimException
  {
    Response response;
    if(post)
    {
      AttributeSet attributeSet = null;
      AttributeSet excludedAttributeSet = null;
      if(attributes != null && attributes.size() > 0)
      {
        if(!excluded)
        {
          attributeSet = attributes;
        }
        else
        {
          excludedAttributeSet = attributes;
        }
      }

      SearchRequest searchRequest = new SearchRequest(attributeSet,
          excludedAttributeSet, filter, sortBy, sortOrder, startIndex, count);

      response = target.path(
          ApiConstants.SEARCH_WITH_POST_PATH_EXTENSION).
          request(ScimService.MEDIA_TYPE_SCIM_TYPE,
              MediaType.APPLICATION_JSON_TYPE).
          post(Entity.entity(searchRequest, MEDIA_TYPE_SCIM_TYPE));
    }
    else
    {
      response = buildRequest().get();
    }
      if (response.getStatusInfo().getFamily() ==
          Response.Status.Family.SUCCESSFUL)
      {
        InputStream inputStream = response.readEntity(InputStream.class);
        ObjectMapper objectMapper = SchemaUtils.createSCIMCompatibleMapper();
        try
        {
          JsonParser parser =
              objectMapper.getFactory().createParser(inputStream);
          try
          {
            parser.nextToken();
            boolean stop = false;
            while (!stop && parser.nextToken() != JsonToken.END_OBJECT)
            {
              String field = parser.getCurrentName();
              parser.nextToken();
              if (field.equals("schemas"))
              {
                parser.skipChildren();
              } else if (field.equals("totalResults"))
              {
                resultHandler.totalResults(parser.getIntValue());
              } else if (field.equals("startIndex"))
              {
                resultHandler.startIndex(parser.getIntValue());
              } else if (field.equals("itemsPerPage"))
              {
                resultHandler.itemsPerPage(parser.getIntValue());
              } else if (field.equals("Resources"))
              {
                while (parser.nextToken() != JsonToken.END_ARRAY)
                {
                  if (!resultHandler.resource(parser.readValueAs(cls)))
                  {
                    stop = true;
                    break;
                  }
                }
              } else if (SchemaUtils.isUrn(field))
              {
                resultHandler.extension(
                    field, parser.readValueAs(ObjectNode.class));
              } else
              {
                // Just skip this field
                parser.nextToken();
              }
            }
          }
          finally
          {
            parser.close();
          }
        }
        catch (IOException e)
        {
          throw new ResponseProcessingException(response, e);
        }
      }
      else
      {
        throw toScimException(response);
      }
  }
}
