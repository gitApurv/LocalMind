package com.localmind.memory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.localmind.memory.entity.UserMemory;
import com.localmind.memory.repository.UserMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MemoryControllerTest {

    private static final String USER_ID = "memory-user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMemoryRepository userMemoryRepository;

    private UserMemory memory;

    @BeforeEach
    void setUp() {
        userMemoryRepository.deleteAll();
        memory = userMemoryRepository
                .save(UserMemory.builder().userId(USER_ID).memoryFact("Prefers dark mode").build());
        userMemoryRepository.save(UserMemory.builder().userId("someone-else").memoryFact("Not yours").build());
    }

    @Test
    @DisplayName("GET /api/memories should list only the caller's memories")
    void listsOwnMemories() throws Exception {
        mockMvc.perform(get("/api/memories").with(user(USER_ID))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].memoryFact", is("Prefers dark mode")))
                .andExpect(jsonPath("$[0].pinned", is(false)));
    }

    @Test
    @DisplayName("PATCH /api/memories/{id} should pin a memory")
    void pinsMemory() throws Exception {
        mockMvc.perform(patch("/api/memories/" + memory.getId()).with(user(USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content("{\"pinned\":true}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned", is(true)));

        assertThat(userMemoryRepository.findByUserIdAndPinnedTrue(USER_ID)).hasSize(1);
    }

    @Test
    @DisplayName("DELETE /api/memories/{id} should delete the memory")
    void deletesMemory() throws Exception {
        mockMvc.perform(delete("/api/memories/" + memory.getId()).with(user(USER_ID)))
                .andExpect(status().isNoContent());

        assertThat(userMemoryRepository.findById(memory.getId())).isEmpty();
    }

    @Test
    @DisplayName("Another user's memory should be reported as not found")
    void otherUsersMemoryIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/memories/" + memory.getId()).with(user("intruder")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message", is("Memory not found")));

        assertThat(userMemoryRepository.findById(memory.getId())).isPresent();
    }

    private static RequestPostProcessor user(String userId) {
        return jwt().jwt(token -> token.subject(userId).claim("userId", userId));
    }
}
