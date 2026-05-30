package com.nook.biz.trade.controller;

import com.nook.biz.trade.service.TradeSubscriptionService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端 - 会员聚合订阅 URL Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/portal/sub")
public class TradeSubUrlController {

    @Resource
    private TradeSubscriptionService subscriptionService;

    /** 订阅内容: Base64 vmess 列表; token 无效返 404. */
    @GetMapping(value = "/{token}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> subscribe(@PathVariable("token") String token) {
        String content = subscriptionService.renderSubscription(token);
        if (content == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(content);
    }
}
