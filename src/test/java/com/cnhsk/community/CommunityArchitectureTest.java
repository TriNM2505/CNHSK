package com.cnhsk.community;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnhsk.community.comment.Comment;
import com.cnhsk.community.comment.CommentRepository;
import com.cnhsk.community.moderation.ModerationCase;
import com.cnhsk.community.moderation.ModerationCaseRepository;
import com.cnhsk.community.post.Post;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.community.reaction.Reaction;
import com.cnhsk.community.reaction.ReactionId;
import com.cnhsk.community.reaction.ReactionRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;

@AnalyzeClasses(
        packages = "com.cnhsk.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class CommunityArchitectureTest {

    // AC-03: include every internal package, including future packages.
    @ArchTest
    static final ArchRule communityUsesOnlyAuthPublicApi =
            noClasses().that().resideInAPackage("com.cnhsk.community..")
                    .should().dependOnClassesThat(resideInAPackage("com.cnhsk.auth..")
                            .and(resideOutsideOfPackage("com.cnhsk.auth.api..")))
                    .because("community accesses auth only through its public API")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule communityUsesOnlyLearningPublicApi =
            noClasses().that().resideInAPackage("com.cnhsk.community..")
                    .should().dependOnClassesThat(resideInAPackage("com.cnhsk.learning..")
                            .and(resideOutsideOfPackage("com.cnhsk.learning.api..")))
                    .because("community accesses learning only through its public API")
                    .allowEmptyShould(false);

    @ArchTest
    static void communityHasExactlyTheFourMappedEntities(JavaClasses classes) {
        Set<Class<?>> entities = classes.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                .map(javaClass -> (Class<?>) javaClass.reflect())
                .collect(Collectors.toSet());

        assertEquals(Set.of(Post.class, Comment.class, Reaction.class, ModerationCase.class), entities);
    }

    @ArchTest
    static void entitiesUseExplicitCommunityTablesAndScalarReferences(JavaClasses classes) {
        Map<Class<?>, String> tables = Map.of(
                Post.class, "posts",
                Comment.class, "comments",
                Reaction.class, "reactions",
                ModerationCase.class, "moderation_cases");

        for (var javaClass : classes) {
            if (!javaClass.isAnnotatedWith(Entity.class)) {
                continue;
            }
            Class<?> entity = javaClass.reflect();
            Table table = entity.getAnnotation(Table.class);
            assertNotNull(table, entity.getName());
            assertEquals("community", table.schema(), entity.getName());
            assertEquals(tables.get(entity), table.name(), entity.getName());
            for (var field : entity.getDeclaredFields()) {
                assertNoRelationship(field);
            }
            for (var method : entity.getDeclaredMethods()) {
                assertNoRelationship(method);
            }
        }
    }

    @ArchTest
    static void repositoriesProvideOnlyTheJpaFoundation(JavaClasses classes) {
        Map<Class<?>, Class<?>> entities = Map.of(
                PostRepository.class, Post.class,
                CommentRepository.class, Comment.class,
                ReactionRepository.class, Reaction.class,
                ModerationCaseRepository.class, ModerationCase.class);
        Set<Class<?>> repositories = classes.stream()
                .map(javaClass -> (Class<?>) javaClass.reflect())
                .filter(JpaRepository.class::isAssignableFrom)
                .collect(Collectors.toSet());

        assertEquals(entities.keySet(), repositories);
        for (Class<?> repository : repositories) {
            assertTrue(repository.isInterface(), repository.getName());
            Set<String> expectedMethods = repository == PostRepository.class
                    ? Set.of("countByAuthorIdAndCreatedAtGreaterThanEqual", "findByIdForUpdate",
                            "findPublished", "countPublished")
                    : repository == CommentRepository.class ? Set.of("countVisibleByPostIds")
                    : repository == ReactionRepository.class ? Set.of("countLikesByPostIds") : Set.of();
            Set<String> actualMethods = Arrays.stream(repository.getDeclaredMethods())
                    .map(Method::getName)
                    .collect(Collectors.toSet());
            assertEquals(expectedMethods, actualMethods, repository.getName());
            assertEquals(expectedMethods.size(), repository.getDeclaredMethods().length,
                    repository.getName() + " must not add overloads or unapproved repository methods");
            assertEquals(0, repository.getDeclaredFields().length, repository.getName());
            Type[] interfaces = repository.getGenericInterfaces();
            assertEquals(1, interfaces.length, repository.getName());
            assertTrue(interfaces[0] instanceof ParameterizedType, repository.getName());
            ParameterizedType jpaRepository = (ParameterizedType) interfaces[0];
            assertEquals(JpaRepository.class, jpaRepository.getRawType(), repository.getName());
            Class<?> idType = repository == ReactionRepository.class ? ReactionId.class : Long.class;
            assertArrayEquals(new Type[] {entities.get(repository), idType},
                    jpaRepository.getActualTypeArguments(), repository.getName());
        }
    }

    private static void assertNoRelationship(AnnotatedElement member) {
        Set<Class<? extends Annotation>> relationships = Set.of(
                ManyToOne.class, OneToMany.class, OneToOne.class, ManyToMany.class,
                ElementCollection.class);
        for (Class<? extends Annotation> relationship : relationships) {
            assertFalse(member.isAnnotationPresent(relationship),
                    member + " must keep references as scalar IDs");
        }
    }
}
