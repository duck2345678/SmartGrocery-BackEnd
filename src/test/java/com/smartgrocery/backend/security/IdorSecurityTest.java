package com.smartgrocery.backend.security;

import com.smartgrocery.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.seeding.enabled=false")
@AutoConfigureMockMvc
public class IdorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(username = "user1@example.com", roles = "CUSTOMER")
    public void whenUserAccessesAnotherUserProfile_thenReturns403() throws Exception {
        // Setup: Current user has ID 1, Target user has ID 2

        // Note: For the logic in SecurityUtils to pick up the ID, 
        // the Principal in the SecurityContext must be a User object.
        // @WithMockUser usually uses a String principal. 
        // We might need a custom SecurityContext or a more simplified test.
        
        // However, we can verify that the GlobalExceptionHandler catches ResourceOwnershipException
        // and returns the correct ApiResponse structure if we simulate the call.
        
        mockMvc.perform(get("/api/v1/users/2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Forbidden"))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
