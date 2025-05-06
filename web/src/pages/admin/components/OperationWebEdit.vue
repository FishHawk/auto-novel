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
    :zh-old="op.old.titleZh"
    :zh-new="op.new.titleZh"
  />
  <TextDiff
    v-if="op.new.introductionZh"
    :zh-old="op.old.introductionZh"
    :zh-new="op.new.introductionZh"
  />

  <TextDiff v-for="(toc, idx) of op.toc" :zh-old="toc.old" :zh-new="toc.new" />
</template>
