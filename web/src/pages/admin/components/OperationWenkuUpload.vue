<script lang="ts" setup>
import { OperationWenkuUpload } from '@/model/Operation';

defineProps<{ op: OperationWenkuUpload }>();
</script>

<template>
  <RouterNA :to="`/wenku/${op.novelId}`">
    {{ op.volumeId }}
  </RouterNA>
</template>
