package com.example.bff.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2B: verifies that scoping the {@link
 * org.springframework.security.web.authentication.HttpStatusEntryPoint} 401
 * behavior to {@code /api/**} does not affect real browser-navigation login
 * initiation, which is handled upstream by
 * {@code OAuth2AuthorizationRequestRedirectFilter} and is outside
 * {@code /api/**}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithAnonymousUser
    void loginInitiationStillRedirectsAnonymousBrowserNavigationToKeycloak() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/bff-app"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        "Location", org.hamcrest.Matchers.startsWith("http://localhost:8081/realms/bff-demo")));
    }
}
