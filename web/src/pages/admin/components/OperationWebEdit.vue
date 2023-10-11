<script lang="ts" setup>
import { OperationWebEdit } from '@/data/api/api_operation_history';
defineProps<{ op: OperationWebEdit }>();
</script>

<template>
  <RouterNA :to="`/novel/${op.providerId}/${op.novelId}`">
    {{ `${op.providerId}/${op.novelId}` }}
  </RouterNA>
  <TextDiff
    v-if="op.new.titleZh"
    :zhOld="op.old.titleZh"
    :zhNew="op.new.titleZh"
  />
  <TextDiff
    v-if="op.new.introductionZh"
    :zhOld="op.old.introductionZh"
    :zhNew="op.new.introductionZh"
  />

  <TextDiff v-for="toc of op.toc" :zhOld="toc.old" :zhNew="toc.new" />
</template>
