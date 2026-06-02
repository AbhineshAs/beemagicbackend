package com.beemagic.controller;

import com.beemagic.entity.Message;
import com.beemagic.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private MessageRepository messageRepository;

    @PostMapping
    public ResponseEntity<?> sendMessage(@RequestBody Message message) {
        messageRepository.save(message);
        return ResponseEntity.ok().build();
    }
}
