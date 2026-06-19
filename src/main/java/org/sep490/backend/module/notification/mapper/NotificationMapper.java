package org.sep490.backend.module.notification.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sep490.backend.module.notification.dto.response.NotificationResponse;
import org.sep490.backend.module.notification.entity.Notification;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(source = "notificationId", target = "id")
    NotificationResponse toResponse(Notification notification);

    List<NotificationResponse> toResponseList(List<Notification> notifications);
}
