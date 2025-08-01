/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.client.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.zowe.apiml.client.configuration.ApplicationConfiguration;
import org.zowe.apiml.client.configuration.SecurityConfiguration;
import org.zowe.apiml.client.configuration.SpringComponentsConfiguration;
import org.zowe.apiml.client.model.Pet;
import org.zowe.apiml.client.service.PetService;

import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = {PetController.class})
@Import(value = {SecurityConfiguration.class, SpringComponentsConfiguration.class, ApplicationConfiguration.class})
class PetControllerPostPetTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PetService petService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void addPetWithValidObject() throws Exception {
        String name = "Linux";
        int id = 1;
        Pet pet = new Pet(null, name);
        String payload = mapper.writeValueAsString(pet);
        when(petService.save(any(Pet.class))).thenReturn(new Pet((long) id, name));

        this.mockMvc.perform(
            post("/api/v1/pets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(id)))
            .andExpect(jsonPath("$.name", is(name)));

        verify(petService, times(1)).save(any(Pet.class));
    }

}
