<script lang="ts" setup>
import { OperationWenkuUpload } from '@/data/api/api_operation';

defineProps<{ op: OperationWenkuUpload }>();
</script>

<template>
  <RouterNA :to="`/wenku/${op.novelId}`">
    {{ op.volumeId }}
  </RouterNA>
</template>
