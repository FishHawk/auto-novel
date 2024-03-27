<script lang="ts" setup>
import { OperationWenkuEditGlossary } from '@/model/Operation';

defineProps<{ op: OperationWenkuEditGlossary }>();
</script>

<template>
  <c-a :to="`/wenku/${op.novelId}`">
    {{ op.novelId }}
  </c-a>
  <table style="border-spacing: 16px 0px; font-size: 12px">
    <tr v-for="(termZh, termJp) in op.old">
      <td>{{ termJp }}</td>
      <td nowrap="nowrap">=></td>
      <td>{{ termZh }}</td>
    </tr>
    <tr>
      <td>分割</td>
    </tr>
    <tr v-for="(termZh, termJp) in op.new">
      <td>{{ termJp }}</td>
      <td nowrap="nowrap">=></td>
      <td>{{ termZh }}</td>
    </tr>
  </table>
</template>
