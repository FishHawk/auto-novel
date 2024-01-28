<script lang="ts" setup>
import { SearchFilled } from '@vicons/material';
import { DropdownOption, NFlex, NTag } from 'naive-ui';
import { computed, h } from 'vue';

const props = defineProps<{
  value: string;
  suggestions: string[];
  tags: string[];
}>();

const emit = defineEmits<{
  'update:value': [string];
  select: [string];
}>();

const handleSelect = (key: string) => {
  emit('select', key);
};

const options = computed(() => {
  const optionsBuffer: DropdownOption[] = [];

  if (props.suggestions !== undefined) {
    props.suggestions.forEach((it) =>
      optionsBuffer.push({
        key: it,
        label: it,
      })
    );
  }

  if (props.tags.length > 0) {
    if (optionsBuffer.length > 0) {
      optionsBuffer.push({
        key: 'footer-divider',
        type: 'divider',
      });
    }

    const renderCustomFooter = () =>
      h(
        NFlex,
        {
          align: 'center',
          style: ' width:100%; padding: 8px 12px;',
        },
        props.tags.map((tag) =>
          h(
            NTag,
            {
              type: tag.startsWith('-') ? 'error' : 'success',
              onClick() {
                emit('update:value', [props.value, tag].join(' '));
              },
            },
            { default: () => tag }
          )
        )
      );
    optionsBuffer.push({
      key: 'footer',
      type: 'render',
      render: renderCustomFooter,
    });
  }
  return optionsBuffer;
});
</script>

<template>
  <n-dropdown
    :disabled="options.length === 0"
    trigger="click"
    :options="options"
    @select="handleSelect"
    :animated="false"
    width="trigger"
  >
    <n-input
      v-bind="$attrs"
      :value="value"
      @update:value="(it: string) => emit('update:value', it)"
    >
      <template #suffix>
        <n-icon :component="SearchFilled" />
      </template>
    </n-input>
  </n-dropdown>
</template>
