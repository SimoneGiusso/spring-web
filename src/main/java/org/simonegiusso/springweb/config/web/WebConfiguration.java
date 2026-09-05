package org.simonegiusso.springweb.config.web;

import org.simonegiusso.springweb.config.persistence.TenantHeaderInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantHeaderInterceptor())
            .addPathPatterns("/**")
            .excludePathPatterns("/actuator/**", "/error");
    }
}
