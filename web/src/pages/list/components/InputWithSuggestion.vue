<script lang="ts" setup>
import { SearchOutlined } from '@vicons/material';
import { DropdownOption, InputInst, NFlex, NTag } from 'naive-ui';

const props = defineProps<{
  value: string;
  suggestions: string[];
  tags: string[];
}>();

const emit = defineEmits<{
  'update:value': [string];
  select: [string];
}>();

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
          size: [4, 4],
          align: 'center',
          style: ' width:100%; padding: 8px 12px;',
        },
        props.tags.map((tag) =>
          h(
            NTag,
            {
              type: tag.startsWith('-') ? 'error' : 'success',
              size: 'small',
              style: { cursor: 'pointer' },
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

const inputRef = ref<InputInst>();
const showSuggestions = ref(false);
const toggleSuggestions = () => {
  // Hacky
  showSuggestions.value = !showSuggestions.value;
};
const handleSelect = (key: string) => {
  emit('select', key);
  inputRef.value?.blur();
  showSuggestions.value = false;
};
</script>

<template>
  <n-dropdown
    :disabled="options.length === 0"
    :show="showSuggestions"
    :options="options"
    :animated="false"
    width="trigger"
    @select="handleSelect"
    @clickoutside="toggleSuggestions"
  >
    <n-input
      ref="inputRef"
      v-bind="$attrs"
      clearable
      :value="value"
      @click="toggleSuggestions"
      @keyup.enter="handleSelect(value)"
      @update:value="(it: string) => emit('update:value', it)"
    >
      <template #suffix>
        <n-icon :component="SearchOutlined" />
      </template>
    </n-input>
  </n-dropdown>
</template>

<style>
.n-dropdown-option-body__label {
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
