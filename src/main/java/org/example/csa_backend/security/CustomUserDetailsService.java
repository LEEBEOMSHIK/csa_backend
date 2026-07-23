package org.example.csa_backend.security;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        boolean enabled = "ACTIVE".equals(user.getStatus());
        return org.springframework.security.core.userdetails.User.withUsername(String.valueOf(user.getId()))
                .password(user.getPassword() != null ? user.getPassword() : "")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())))
                .disabled(!enabled)
                .build();
    }
}
