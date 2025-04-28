<script lang="ts" setup>
import { ArrowDropUpFilled, ArrowDropDownFilled } from '@vicons/material';
interface Option {
  value: string;
  label: string;
}
const modelValue = defineModel<{
  value: string;
  desc: boolean;
}>('value', {
  required: true,
});

const props = withDefaults(
  defineProps<{
    options: Option[];
    defaultDesc?: boolean;
  }>(),
  {
    defaultDesc: true,
  },
);

let currentValue = modelValue.value.value;

const handelClick = (option: Option) => {
  let desc = false;
  if (currentValue != option.value) {
    // 当前切换了选项，重置desc
    currentValue = option.value;
    desc = props.defaultDesc;
  } else {
    desc = !modelValue.value.desc;
  }
  modelValue.value = {
    value: option.value,
    desc,
  };
};
</script>
<template>
  <n-button-group size="small">
    <template v-for="(option, idx) in options" :key="idx + '_' + option">
      <n-button
        :type="option.value === modelValue.value ? 'success' : 'default'"
        @click="handelClick(option)"
        ghost
      >
        <template #icon v-if="option.value === modelValue.value">
          <n-icon>
            <ArrowDropDownFilled v-if="modelValue.desc" />
            <ArrowDropUpFilled v-else />
          </n-icon>
        </template>
        {{ option.label }}
      </n-button>
    </template>
  </n-button-group>
</template>
