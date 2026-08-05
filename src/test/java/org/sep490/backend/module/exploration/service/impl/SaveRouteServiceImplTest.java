package org.sep490.backend.module.exploration.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.exploration.dto.response.SavedRouteResponse;
import org.sep490.backend.module.exploration.entity.SavedRoute;
import org.sep490.backend.module.exploration.mapper.SavedRouteMapper;
import org.sep490.backend.module.exploration.repository.SavedRouteRepository;
import org.sep490.backend.module.user.service.UserService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test cho luồng LƯU TUYẾN ĐƯỜNG (Exploration).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaveRouteServiceImplTest {

    @Mock private SavedRouteRepository savedRouteRepository;
    @Mock private UserService userService;
    @Mock private RouteService routeService;
    @Mock private SavedRouteMapper savedRouteMapper;

    @InjectMocks private SaveRouteServiceImpl service;

    private User user(Long id) {
        User u = new User();
        u.setUserId(id);
        return u;
    }

    private Route route(Long id) {
        Route r = new Route();
        r.setRouteId(id);
        return r;
    }

    // =====================================================================
    // Function: saveRoute
    // =====================================================================
    @Nested
    @DisplayName("saveRoute")
    class SaveRouteTest {

        // UTCID01 - Abnormal: tuyến đường không tồn tại
        @Test
        void saveRoute_routeNotFound_throwsRouteNotExist() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(routeService.getById(10L)).thenThrow(new BusinessException("Tuyến đường không tồn tại"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.saveRoute(10L));

            assertEquals("Tuyến đường không tồn tại", ex.getMessage());
        }

        // UTCID02 - Abnormal: tuyến đường đã được lưu trước đó
        @Test
        void saveRoute_alreadySaved_throwsAlreadyExists() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(routeService.getById(10L)).thenReturn(route(10L));
            when(savedRouteRepository.existsByRoute_RouteIdAndUser_UserId(10L, 1L)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.saveRoute(10L));

            assertEquals("Tuyến đường đã tồn tại", ex.getMessage());
            verify(savedRouteRepository, never()).save(any());
        }

        // UTCID03 - Normal: lưu tuyến đường thành công
        @Test
        void saveRoute_valid_savesRoute() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(routeService.getById(10L)).thenReturn(route(10L));
            when(savedRouteRepository.existsByRoute_RouteIdAndUser_UserId(10L, 1L)).thenReturn(false);
            SavedRoute savedRoute = SavedRoute.builder().build();
            when(savedRouteMapper.toEntity(10L, 1L)).thenReturn(savedRoute);
            when(savedRouteMapper.toResponse(savedRoute)).thenReturn(new SavedRouteResponse());

            SavedRouteResponse response = service.saveRoute(10L);

            assertNotNull(response);
            verify(savedRouteRepository).save(savedRoute);
        }
    }

    // =====================================================================
    // Function: unsaveRoute
    // =====================================================================
    @Nested
    @DisplayName("unsaveRoute")
    class UnsaveRouteTest {

        // UTCID01 - Abnormal: bản ghi lưu không tồn tại
        @Test
        void unsaveRoute_notFound_throwsNotFound() {
            when(savedRouteRepository.findById(50L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.unsaveRoute(50L));

            assertEquals("Tuyến đường không tồn tại", ex.getMessage());
        }

        // UTCID02 - Abnormal: bản ghi thuộc về người dùng khác
        @Test
        void unsaveRoute_notOwner_throwsNotOwner() {
            SavedRoute savedRoute = SavedRoute.builder().user(user(1L)).build();
            when(savedRouteRepository.findById(50L)).thenReturn(Optional.of(savedRoute));
            when(userService.getCurrentUser()).thenReturn(user(2L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.unsaveRoute(50L));

            assertEquals("Bạn không có thể bỏ lưu tuyến đường này", ex.getMessage());
            verify(savedRouteRepository, never()).delete(any());
        }

        // UTCID03 - Normal: bỏ lưu thành công (cùng một user object)
        @Test
        void unsaveRoute_owner_deletesSavedRoute() {
            User owner = user(1L);
            SavedRoute savedRoute = SavedRoute.builder().user(owner).build();
            when(savedRouteRepository.findById(50L)).thenReturn(Optional.of(savedRoute));
            when(userService.getCurrentUser()).thenReturn(owner);

            service.unsaveRoute(50L);

            verify(savedRouteRepository).delete(savedRoute);
        }
    }
}
