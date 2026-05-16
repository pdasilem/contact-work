package com.pdasilem.contactwork.project.asset;

import org.springframework.core.io.Resource;

public record MailAttachment(String filename, Resource resource) {
}
