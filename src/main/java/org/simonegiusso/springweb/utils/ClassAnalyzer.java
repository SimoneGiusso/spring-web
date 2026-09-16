package org.simonegiusso.springweb.utils;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Null;
import jakarta.validation.metadata.ElementDescriptor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.simonegiusso.springweb.product.ProductDTO;
import org.simonegiusso.springweb.product.validation.OnCreate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class ClassAnalyzer {

    private final Validator validator;

    public List<String> fieldsWithoutNullConstraint(Class<?> clazz) {
        return Arrays.stream(clazz.getRecordComponents())
            .map(RecordComponent::getName)
            .filter(this::isWritableOnCreate)
            .toList();
    }

    private boolean isWritableOnCreate(String property) {
        var constraints = validator.getConstraintsForClass(ProductDTO.class).getConstraintsForProperty(property);
        return constraints == null || doesNotContainNullConstraint(constraints);
    }

    private static boolean doesNotContainNullConstraint(ElementDescriptor constraints) {
        return constraints.findConstraints()
            .unorderedAndMatchingGroups(OnCreate.class)
            .getConstraintDescriptors().stream()
            .noneMatch(constraint -> constraint.getAnnotation() instanceof Null);
    }

}
