package com.aws.greengrass.device.configuration;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@JsonDeserialize(builder = GroupDefinition.GroupDefinitionBuilder.class)
public class GroupDefinition {

    String selectionRule;

    // RuleExpressionNode ruleExpressionTree;

    String roleName;

    @Builder
    GroupDefinition(@NonNull String selectionRule, @NonNull String roleName) {
        this.selectionRule = selectionRule;
        //TODO build binary expression tree from rule string
        // this.ruleExpressionTree = null;
        this.roleName = roleName;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class GroupDefinitionBuilder {
    }
}
