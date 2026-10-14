import { defineProfile } from '../types';
import { reasoningFields, resolveReasoningEffort } from './openai';

export const profile = defineProfile({
  id: 'openai-responses',
  apiFormat: 'openai-responses',
  label: 'OpenAI Responses',
  fields: reasoningFields,
  buildRequestParams: (values) => {
    const effort = resolveReasoningEffort(values);
    return effort ? { reasoning: { effort } } : {};
  },
});
