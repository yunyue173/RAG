package com.example.system.config;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.system.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = request.getRequestURI();
        if (isPublicPath(path) || request.getSession(false) != null
                && request.getSession(false).getAttribute(AuthService.SESSION_USER_ID) != null) {
            return true;
        }

        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"请先登录\"}");
            return false;
        }

        response.sendRedirect("/login");
        return false;
    }

    private boolean isPublicPath(String path) {
        return path.equals("/login")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.equals("/favicon.ico")
                || path.equals("/error");
    }
}
