package xyz.kuailemao.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleControllerPaginationValidationTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;
    private static Method blogFeed;
    private static Method backList;

    @BeforeAll
    static void setUp() throws NoSuchMethodException {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        blogFeed = ArticleController.class.getMethod("blogFeed", Long.class, Integer.class, Integer.class);
        backList = ArticleController.class.getMethod("listArticle", Integer.class, Integer.class);
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void acceptsValidPublicAndAdminPagination() {
        assertTrue(validate(blogFeed, new Object[]{null, 1, 9}).isEmpty());
        assertTrue(validate(backList, new Object[]{1, 50}).isEmpty());
    }

    @Test
    void rejectsPageNumberBelowOne() {
        assertMessage(validate(blogFeed, new Object[]{null, 0, 9}), "页码不能小于1");
    }

    @Test
    void rejectsPageSizeBelowOne() {
        assertMessage(validate(backList, new Object[]{1, 0}), "每页数量不能小于1");
    }

    @Test
    void rejectsPageSizeAboveOneHundred() {
        assertMessage(validate(blogFeed, new Object[]{null, 1, 101}), "每页数量不能超过100");
    }

    private Set<ConstraintViolation<ArticleController>> validate(Method method, Object[] parameters) {
        return validator.forExecutables().validateParameters(new ArticleController(), method, parameters);
    }

    private void assertMessage(Set<ConstraintViolation<ArticleController>> violations, String message) {
        assertEquals(1, violations.size());
        assertEquals(message, violations.iterator().next().getMessage());
    }
}
