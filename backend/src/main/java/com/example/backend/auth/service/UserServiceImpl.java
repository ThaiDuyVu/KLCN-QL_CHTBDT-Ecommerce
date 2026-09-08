package com.example.backend.auth.service;

import com.example.backend.auth.dto.UserPageResponse;
import com.example.backend.auth.dto.UserResponse;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.exception.EmailAlreadyExistsException;
import com.example.backend.auth.exception.UserNotFoundException;
import com.example.backend.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.backend.auth.dto.UpdateUserRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserResponse getUserById(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "Không tìm thấy người dùng với ID: " + userId
                        )
                );

        return new UserResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getDisplayName(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
    @Override
    public UserPageResponse getUsers(int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<User> userPage = userRepository.findAll(pageable);

        var content = userPage.getContent()
                .stream()
                .map(user -> new UserResponse(
                        user.getUserId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getPhone(),
                        user.getDisplayName(),
                        user.getStatus(),
                        user.getCreatedAt()
                ))
                .toList();

        return new UserPageResponse(
                content,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages()
        );
    }
    @Override
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "Không tìm thấy người dùng với ID: " + userId
                        )
                );

        if (userRepository.existsByEmailAndUserIdNot(
                request.getEmail(),
                userId
        )) {
            throw new EmailAlreadyExistsException(
                    "Email đã được sử dụng bởi người dùng khác: "
                            + request.getEmail()
            );
        }

        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());

        User updatedUser = userRepository.save(user);

        return new UserResponse(
                updatedUser.getUserId(),
                updatedUser.getUsername(),
                updatedUser.getEmail(),
                updatedUser.getPhone(),
                updatedUser.getDisplayName(),
                updatedUser.getStatus(),
                updatedUser.getCreatedAt()
        );
    }
}