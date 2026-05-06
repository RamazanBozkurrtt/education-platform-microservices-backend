package com.edubase.search.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "course-search-v1")
public class CourseSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String instructorId;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Keyword)
    private String status;

    @Builder.Default
    @Field(type = FieldType.Keyword)
    private List<String> tags = new ArrayList<>();

    @Builder.Default
    @Field(type = FieldType.Text)
    private List<String> learningOutcomes = new ArrayList<>();

    @Field(type = FieldType.Double)
    private Double averageRating;

    @Field(type = FieldType.Long)
    private Long ratingCount;

    @Field(type = FieldType.Boolean)
    private Boolean searchable;

    @Field(type = FieldType.Long)
    private Long courseEventVersion;

    @Field(type = FieldType.Long)
    private Long ratingEventVersion;

    @Field(type = FieldType.Date)
    private Instant updatedAt;
}
