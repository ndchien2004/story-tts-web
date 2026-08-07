package com.storytts.backend.security;

import com.storytts.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserByUsername(String login) throws UsernameNotFoundException {
        return userRepository.findByUsernameOrEmail(login)
                .map(AppUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản: " + login));
    }

    /**
     * Nạp lại người dùng theo id ở mỗi request có JWT.
     * Nhờ vậy khi Admin thu hồi VIP, quyền bị mất ngay lập tức thay vì phải đợi token hết hạn.
     */
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserById(Long id) {
        return userRepository.findById(id)
                .map(AppUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản id=" + id));
    }
}
