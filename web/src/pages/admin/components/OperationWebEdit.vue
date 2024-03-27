<script lang="ts" setup>
import { OperationWebEdit } from '@/model/Operation';

defineProps<{ op: OperationWebEdit }>();
</script>

<template>
  <c-a :to="`/novel/${op.providerId}/${op.novelId}`">
    {{ `${op.providerId}/${op.novelId}` }}
  </c-a>
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
