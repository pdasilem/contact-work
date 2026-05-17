package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.mail.GmailAliasService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class GmailOAuthController {

    private final GmailAliasService gmailAliasService;

    public GmailOAuthController(GmailAliasService gmailAliasService) {
        this.gmailAliasService = gmailAliasService;
    }

    @GetMapping("/callback")
    public RedirectView callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        if (error != null && !error.isBlank()) {
            return redirect("failed", state, "Google OAuth failed: " + error);
        }
        try {
            UUID projectId = UUID.fromString(state);
            gmailAliasService.exchangeCodeAndSyncAlias(projectId, code);
            return redirect("success", projectId.toString(), null);
        } catch (Exception ex) {
            return redirect("failed", state, ex.getMessage());
        }
    }

    private RedirectView redirect(String result, String projectId, String message) {
        StringBuilder target = new StringBuilder("/app?gmailAlias=").append(encode(result));
        if (projectId != null && !projectId.isBlank()) {
            target.append("&projectId=").append(encode(projectId));
        }
        if (message != null && !message.isBlank()) {
            target.append("&message=").append(encode(message));
        }
        return new RedirectView(target.toString());
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
