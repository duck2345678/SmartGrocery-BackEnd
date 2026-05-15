package com.smartgrocery.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartgrocery.backend.entity.Role;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.RoleRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserBanFlowTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;
    @Mock private AccountEmailService accountEmailService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private AdminUserManagementService service;

    @Test
    void banThenUnbanShouldPersistAndNotifyEmail() {
        User admin = User.builder().id(1L).role(Role.builder().name("ADMIN").build()).build();
        User target = User.builder().id(9L).email("u@x.com").status("ACTIVE").role(Role.builder().name("CUSTOMER").build()).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0, User.class));
        when(objectMapper.createObjectNode()).thenReturn(new ObjectNode(new ObjectMapper().getNodeFactory()));

        service.setStatus(admin, 9L, "INACTIVE", "violation");
        assertEquals("INACTIVE", target.getStatus());
        service.setStatus(admin, 9L, "ACTIVE", "recovered");
        assertEquals("ACTIVE", target.getStatus());
        verify(accountEmailService, times(2)).sendBanStatusEmail(eq(target), anyString(), anyBoolean());
    }
}
