import {
  defineProfile,
  DEFAULT_FIELD_VALUE,
  type ProfileValues,
} from '../types';

export const reasoningFields = [
  {
    key: 'reasoning_effort',
    label: '思考强度',
    type: 'select',
    options: [
      { label: '默认', value: DEFAULT_FIELD_VALUE },
      { label: 'none', value: 'none' },
      { label: 'minimal', value: 'minimal' },
      { label: 'low', value: 'low' },
      { label: 'medium', value: 'medium' },
      { label: 'high', value: 'high' },
      { label: 'xhigh', value: 'xhigh' },
      { label: 'max', value: 'max' },
    ],
  },
] as const;

export const resolveReasoningEffort = (
  values: ProfileValues<typeof reasoningFields>,
): string | undefined => {
  const effort = values.reasoning_effort;
  if (!effort || effort === DEFAULT_FIELD_VALUE) return undefined;
  return effort;
};

export const profile = defineProfile({
  id: 'openai',
  apiFormat: 'openai',
  label: 'OpenAI Chat Completions',
  fields: reasoningFields,
  buildRequestParams: (values) => {
    const effort = resolveReasoningEffort(values);
    return effort ? { reasoning_effort: effort } : {};
  },
});
