package software.amazon.awssdk.codegen.lite.regions;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import software.amazon.awssdk.annotations.Generated;
import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.codegen.lite.PoetClass;
import software.amazon.awssdk.codegen.lite.regions.model.Endpoint;
import software.amazon.awssdk.codegen.lite.regions.model.Partition;
import software.amazon.awssdk.codegen.lite.regions.model.Partitions;
import software.amazon.awssdk.codegen.lite.regions.model.Service;
import software.amazon.awssdk.utils.StringUtils;
import software.amazon.awssdk.utils.internal.CodegenNamingUtils;

public class EndpointTagGenerator implements PoetClass {
    private final Partitions partitions;
    private final String basePackage;

    public EndpointTagGenerator(Partitions partitions, String basePackage) {
        this.partitions = partitions;
        this.basePackage = basePackage;
    }

    @Override
    public TypeSpec poetClass() {
        TypeSpec.Builder builder = TypeSpec.classBuilder(className())
                .addModifiers(FINAL, PUBLIC)
                .addJavadoc(documentation())
                .addAnnotation(SdkPublicApi.class)
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", "software.amazon.awssdk:codegen")
                        .build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(PRIVATE)
                        .addParameter(String.class, "id")
                        .addStatement("this.id = id")
                        .build());

        endpointTags(builder);

        builder.addField(FieldSpec.builder(String.class, "id")
                .addModifiers(FINAL, PRIVATE)
                .build())
                .addMethod(tagOf())
                .addMethod(tagGetter())
                .addMethod(id())
                .addMethod(tagToString());

        return builder.addType(cache()).build();
    }

    // método alterado
    private void endpointTags(TypeSpec.Builder builder) {
        Set<String> allTags = collectAllTags();
        addTagFields(builder, allTags);
        addTagsListField(builder, allTags);
    }

    // método auxiliar novo
    private Set<String> collectAllTags() {
        List<Partition> partitionsList = partitions.getPartitions();

        Stream<Endpoint> endpointsFromPartitions = partitionsList.stream()
                .map(Partition::getDefaults)
                .filter(Objects::nonNull);

        Stream<Endpoint> endpointsFromServices = partitionsList.stream()
                .flatMap(p -> p.getServices().values().stream())
                .flatMap(s -> s.getEndpoints().values().stream());

        Stream<Endpoint> endpointsFromServiceDefaults = partitionsList.stream()
                .flatMap(p -> p.getServices().values().stream())
                .map(Service::getDefaults)
                .filter(Objects::nonNull);

        return Stream.of(
                endpointsFromPartitions,
                endpointsFromServices,
                endpointsFromServiceDefaults)
                .flatMap(s -> s)
                .flatMap(this::extractTags)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Stream<String> extractTags(Endpoint endpoint) {
        return endpoint.getVariants().stream()
                .map(v -> v.getTags())
                .filter(Objects::nonNull)
                .flatMap(List::stream);
    }

    // método auxiliar novo
    private void addTagFields(TypeSpec.Builder builder, Set<String> tags) {
        tags.forEach(tag -> builder.addField(FieldSpec.builder(className(), enumValueForTagId(tag))
                .addModifiers(PUBLIC, STATIC, FINAL)
                .initializer("$T.of($S)", className(), tag)
                .build()));
    }

    // método auxiliar novo
    private void addTagsListField(TypeSpec.Builder builder, Set<String> tags) {
        CodeBlock.Builder cb = CodeBlock.builder()
                .add("$T.unmodifiableList($T.asList(", Collections.class, Arrays.class);

        boolean first = true;
        for (String tag : tags) {
            if (!first) {
                cb.add(", ");
            }
            cb.add("$L", enumValueForTagId(tag));
            first = false;
        }

        cb.add("))");

        TypeName listOfTags = ParameterizedTypeName.get(
                ClassName.get(List.class),
                className());

        builder.addField(FieldSpec.builder(listOfTags, "ENDPOINT_TAGS")
                .addModifiers(PRIVATE, STATIC, FINAL)
                .initializer(cb.build())
                .build());
    }

    private String enumValueForTagId(String tag) {
        return Stream.of(CodegenNamingUtils.splitOnWordBoundaries(tag))
                .map(StringUtils::upperCase)
                .collect(Collectors.joining("_"));
    }

    private MethodSpec tagOf() {
        return MethodSpec.methodBuilder("of")
                .addModifiers(PUBLIC, STATIC)
                .addParameter(String.class, "id")
                .returns(className())
                .addStatement("return EndpointTagCache.put($L)", "id")
                .build();
    }

    private MethodSpec id() {
        return MethodSpec.methodBuilder("id")
                .addModifiers(PUBLIC)
                .returns(String.class)
                .addStatement("return this.id")
                .build();
    }

    private MethodSpec tagGetter() {
        return MethodSpec.methodBuilder("endpointTags")
                .addModifiers(PUBLIC, STATIC)
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), className()))
                .addStatement("return ENDPOINT_TAGS")
                .build();
    }

    private MethodSpec tagToString() {
        return MethodSpec.methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(String.class)
                .addStatement("return id")
                .build();
    }

    private TypeSpec cache() {
        ParameterizedTypeName mapOfStringTags = ParameterizedTypeName.get(ClassName.get(ConcurrentHashMap.class),
                ClassName.get(String.class),
                className());

        return TypeSpec.classBuilder("EndpointTagCache")
                .addModifiers(PRIVATE, STATIC)
                .addField(FieldSpec.builder(mapOfStringTags, "IDS")
                        .addModifiers(PRIVATE, STATIC, FINAL)
                        .initializer("new $T<>()", ConcurrentHashMap.class)
                        .build())
                .addMethod(MethodSpec.constructorBuilder().addModifiers(PRIVATE).build())
                .addMethod(MethodSpec.methodBuilder("put")
                        .addModifiers(PRIVATE, STATIC)
                        .addParameter(String.class, "id")
                        .returns(className())
                        .addStatement("return IDS.computeIfAbsent(id, $T::new)", className())
                        .build())
                .build();
    }

    private CodeBlock documentation() {
        return CodeBlock.builder()
                .add("A tag applied to endpoints to specify that they're to be used in certain contexts. For example, "
                        + "FIPS tags are applied to endpoints discussed here: https://aws.amazon.com/compliance/fips/ and "
                        + "DUALSTACK tags are applied to endpoints that can return IPv6 addresses.")
                .build();
    }

    @Override
    public ClassName className() {
        return ClassName.get(basePackage, "EndpointTag");
    }
}