/*
 * MIT License
 *
 * Copyright (c) 2020 Airbyte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.airbyte.server.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.airbyte.api.model.CheckConnectionRead;
import io.airbyte.api.model.CheckConnectionRead.StatusEnum;
import io.airbyte.api.model.DestinationCreate;
import io.airbyte.api.model.DestinationIdRequestBody;
import io.airbyte.api.model.DestinationRead;
import io.airbyte.api.model.DestinationRecreate;
import io.airbyte.commons.json.JsonValidationException;
import io.airbyte.config.DestinationConnectionImplementation;
import io.airbyte.config.StandardDestination;
import io.airbyte.config.persistence.ConfigNotFoundException;
import io.airbyte.server.errors.KnownException;
import io.airbyte.server.helpers.DestinationDefinitionHelpers;
import io.airbyte.server.helpers.DestinationHelpers;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class WebBackendDestinationHandlerTest {

  private WebBackendDestinationHandler wbDestinationImplementationHandler;

  private DestinationHandler destinationHandler;
  private SchedulerHandler schedulerHandler;

  private DestinationRead destinationRead;

  @BeforeEach
  public void setup() throws IOException {
    destinationHandler = mock(DestinationHandler.class);
    schedulerHandler = mock(SchedulerHandler.class);
    wbDestinationImplementationHandler = new WebBackendDestinationHandler(destinationHandler, schedulerHandler);

    final StandardDestination standardDestination = DestinationDefinitionHelpers.generateDestination();
    DestinationConnectionImplementation destinationImplementation =
        DestinationHelpers.generateDestination(UUID.randomUUID());
    destinationRead = DestinationHelpers.getDestinationImplementationRead(destinationImplementation, standardDestination);
  }

  @Test
  public void testCreatesDestinationWhenCheckConnectionSucceeds() throws JsonValidationException, IOException, ConfigNotFoundException {
    DestinationCreate destinationCreate = new DestinationCreate();
    destinationCreate.setName(destinationRead.getName());
    destinationCreate.setConnectionConfiguration(destinationRead.getConnectionConfiguration());
    destinationCreate.setDestinationDefinitionId(destinationRead.getDestinationDefinitionId());
    destinationCreate.setWorkspaceId(destinationRead.getWorkspaceId());

    when(destinationHandler.createDestination(destinationCreate))
        .thenReturn(destinationRead);

    DestinationIdRequestBody destinationIdRequestBody = new DestinationIdRequestBody();
    destinationIdRequestBody.setDestinationId(destinationRead.getDestinationId());

    CheckConnectionRead checkConnectionRead = new CheckConnectionRead();
    checkConnectionRead.setStatus(StatusEnum.SUCCESS);

    when(schedulerHandler.checkDestinationConnection(destinationIdRequestBody)).thenReturn(checkConnectionRead);

    DestinationRead returnedDestination =
        wbDestinationImplementationHandler.webBackendCreateDestinationAndCheck(destinationCreate);

    verify(destinationHandler, times(0)).deleteDestination(Mockito.any());
    assertEquals(destinationRead, returnedDestination);
  }

  @Test
  public void testDeletesDestinationWhenCheckConnectionFails() throws JsonValidationException, IOException, ConfigNotFoundException {
    DestinationCreate destinationCreate = new DestinationCreate();
    destinationCreate.setName(destinationRead.getName());
    destinationCreate.setConnectionConfiguration(destinationRead.getConnectionConfiguration());
    destinationCreate.setDestinationDefinitionId(destinationRead.getDestinationDefinitionId());
    destinationCreate.setWorkspaceId(destinationRead.getWorkspaceId());

    when(destinationHandler.createDestination(destinationCreate))
        .thenReturn(destinationRead);

    CheckConnectionRead checkConnectionRead = new CheckConnectionRead();
    checkConnectionRead.setStatus(StatusEnum.FAILURE);

    DestinationIdRequestBody destinationIdRequestBody = new DestinationIdRequestBody();
    destinationIdRequestBody.setDestinationId(destinationRead.getDestinationId());
    when(schedulerHandler.checkDestinationConnection(destinationIdRequestBody)).thenReturn(checkConnectionRead);

    Assertions.assertThrows(KnownException.class,
        () -> wbDestinationImplementationHandler.webBackendCreateDestinationAndCheck(destinationCreate));

    verify(destinationHandler).deleteDestination(destinationIdRequestBody);
  }

  @Test
  public void testReCreatesDestinationWhenCheckConnectionSucceeds() throws JsonValidationException, IOException, ConfigNotFoundException {
    DestinationCreate destinationCreate = new DestinationCreate();
    destinationCreate.setName(destinationRead.getName());
    destinationCreate.setConnectionConfiguration(destinationRead.getConnectionConfiguration());
    destinationCreate.setWorkspaceId(destinationRead.getWorkspaceId());

    DestinationRead newDestinationImplementation = DestinationHelpers
        .getDestinationImplementationRead(DestinationHelpers.generateDestination(UUID.randomUUID()), DestinationDefinitionHelpers
            .generateDestination());

    when(destinationHandler.createDestination(destinationCreate)).thenReturn(newDestinationImplementation);

    DestinationIdRequestBody newDestinationId = new DestinationIdRequestBody();
    newDestinationId.setDestinationId(newDestinationImplementation.getDestinationId());

    CheckConnectionRead checkConnectionRead = new CheckConnectionRead();
    checkConnectionRead.setStatus(StatusEnum.SUCCESS);

    when(schedulerHandler.checkDestinationConnection(newDestinationId)).thenReturn(checkConnectionRead);

    DestinationRecreate destinationRecreate = new DestinationRecreate();
    destinationRecreate.setName(destinationRead.getName());
    destinationRecreate.setConnectionConfiguration(destinationRead.getConnectionConfiguration());
    destinationRecreate.setWorkspaceId(destinationRead.getWorkspaceId());
    destinationRecreate.setDestinationId(destinationRead.getDestinationId());

    DestinationIdRequestBody oldDestinationIdBody = new DestinationIdRequestBody();
    oldDestinationIdBody.setDestinationId(destinationRead.getDestinationId());

    DestinationRead returnedDestination =
        wbDestinationImplementationHandler.webBackendRecreateDestinationAndCheck(destinationRecreate);

    verify(destinationHandler, times(1)).deleteDestination(Mockito.eq(oldDestinationIdBody));
    assertEquals(newDestinationImplementation, returnedDestination);
  }

  @Test
  public void testRecreateDeletesNewCreatedDestinationWhenFails() throws JsonValidationException, IOException, ConfigNotFoundException {
    DestinationCreate destinationCreate = new DestinationCreate();
    destinationCreate.setName(destinationRead.getName());
    destinationCreate.setConnectionConfiguration(destinationRead.getConnectionConfiguration());
    destinationCreate.setWorkspaceId(destinationRead.getWorkspaceId());

    DestinationRead newDestinationImplementation = DestinationHelpers.getDestinationImplementationRead(
        DestinationHelpers.generateDestination(UUID.randomUUID()), DestinationDefinitionHelpers.generateDestination());

    when(destinationHandler.createDestination(destinationCreate)).thenReturn(newDestinationImplementation);

    DestinationIdRequestBody newDestinationId = new DestinationIdRequestBody();
    newDestinationId.setDestinationId(newDestinationImplementation.getDestinationId());

    CheckConnectionRead checkConnectionRead = new CheckConnectionRead();
    checkConnectionRead.setStatus(StatusEnum.FAILURE);

    when(schedulerHandler.checkDestinationConnection(newDestinationId)).thenReturn(checkConnectionRead);

    DestinationRecreate destinationRecreate = new DestinationRecreate();
    destinationRecreate.setName(destinationRead.getName());
    destinationRecreate.setConnectionConfiguration(destinationRead.getConnectionConfiguration());
    destinationRecreate.setWorkspaceId(destinationRead.getWorkspaceId());
    destinationRecreate.setDestinationId(destinationRead.getDestinationId());

    Assertions.assertThrows(KnownException.class,
        () -> wbDestinationImplementationHandler.webBackendRecreateDestinationAndCheck(destinationRecreate));
    verify(destinationHandler, times(1)).deleteDestination(Mockito.eq(newDestinationId));
  }

}
