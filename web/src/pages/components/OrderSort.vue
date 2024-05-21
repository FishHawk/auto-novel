<script lang="ts" setup>
import { ArrowDropUpFilled, ArrowDropDownFilled } from '@vicons/material';
interface Option {
  value: string;
  label: string;
}
const value = defineModel<{
  value: string;
  desc: boolean;
}>('value', {
  required: true,
});
defineProps<{
  options: Option[];
}>();

const handelClick = (option: Option) => {
  // 玄学问题 当传进来的值是ref的时候 只有这样才能触发computed
  // value.value = {
  //   value: option.value,
  //   desc: !value.value.desc,
  // };
  // 玄学问题 当传进来的值是reactive的时候 只有这样才能触发computed
  value.value.value = option.value;
  value.value.desc = !value.value.desc;
};
</script>
<template>
  <n-button-group size="small">
    <template v-for="option in options">
      <n-button
        :type="option.value === value.value ? 'success' : 'default'"
        @click="handelClick(option)"
        ghost
      >
        <template #icon v-if="option.value === value.value">
          <n-icon>
            <ArrowDropDownFilled v-if="value.desc" />
            <ArrowDropUpFilled v-else />
          </n-icon>
        </template>
        {{ option.label }}
      </n-button>
    </template>
  </n-button-group>
</template>
