
package com.hotelpro.security;

import com.hotelpro.model.AppUser;
import com.hotelpro.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        AppUser appUser = userRepository.findByEmail(
                email.trim().toLowerCase()
        ).orElseThrow(() ->
                new UsernameNotFoundException("User not found: " + email)
        );

        return User.withUsername(appUser.email)
                .password(appUser.password)
                .authorities("ROLE_" + appUser.role)
                .build();
    }
}
