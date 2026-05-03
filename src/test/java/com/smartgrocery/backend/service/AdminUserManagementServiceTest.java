package com.smartgrocery.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.dto.AdminUserUpsertRequest;
import com.smartgrocery.backend.entity.Role;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.RoleRepository;
import com.smartgrocery.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserManagementServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;
    @Mock private AccountEmailService accountEmailService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private AdminUserManagementService service;

    private User adminActor() {
        return User.builder().id(1L).role(Role.builder().name("ADMIN").build()).build();
    }

    @Test
    void createUserHashesPasswordAndSaves() {
        AdminUserUpsertRequest req = new AdminUserUpsertRequest();
        req.setFullName("User A");
        req.setEmail("a@x.com");
        req.setPhone("0901");
        req.setPassword("abc12345");
        req.setRoleName("CUSTOMER");
        req.setStatus("ACTIVE");
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(Role.builder().id(2L).name("CUSTOMER").build()));
        when(passwordEncoder.encode("abc12345")).thenReturn("hashed");
        when(objectMapper.createObjectNode()).thenReturn(new ObjectNode(new ObjectMapper().getNodeFactory()));
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0, User.class);
            u.setId(10L);
            return u;
        });

        service.create(adminActor(), req);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed", captor.getValue().getPasswordHash());
        assertEquals("CUSTOMER", captor.getValue().getRole().getName());
    }

    @Test
    void createUserRejectsWeakPassword() {
        AdminUserUpsertRequest req = new AdminUserUpsertRequest();
        req.setFullName("User A");
        req.setEmail("a@x.com");
        req.setPassword("1234567");
        req.setRoleName("CUSTOMER");
        assertThrows(IllegalArgumentException.class, () -> service.create(adminActor(), req));
    }

    @Test
    void updateUserAllowsChangingRoleByAdmin() {
        User target = User.builder()
                .id(7L)
                .email("old@x.com")
                .role(Role.builder().name("CUSTOMER").build())
                .status("ACTIVE")
                .build();
        AdminUserUpsertRequest req = new AdminUserUpsertRequest();
        req.setRoleName("STAFF");
        req.setFullName("Updated");
        when(userRepository.findById(7L)).thenReturn(Optional.of(target));
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(Role.builder().name("STAFF").build()));
        when(objectMapper.createObjectNode()).thenReturn(new ObjectNode(new ObjectMapper().getNodeFactory()));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0, User.class));

        service.update(adminActor(), 7L, req);
        assertEquals("STAFF", target.getRole().getName());
        assertEquals("Updated", target.getFullName());
    }
}
