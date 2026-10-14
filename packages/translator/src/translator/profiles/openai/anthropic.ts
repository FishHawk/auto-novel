import { defineProfile, DEFAULT_FIELD_VALUE } from '../types';

export const profile = defineProfile({
  id: 'anthropic',
  apiFormat: 'anthropic-messages',
  label: 'Anthropic Messages',
  fields: [
    {
      key: 'reasoning_effort',
      label: '思考强度',
      type: 'select',
      options: [
        { label: '默认', value: DEFAULT_FIELD_VALUE },
        { label: 'low', value: 'low' },
        { label: 'medium', value: 'medium' },
        { label: 'high', value: 'high' },
        { label: 'xhigh', value: 'xhigh' },
        { label: 'max', value: 'max' },
      ],
    },
  ],
  buildRequestParams: ({ reasoning_effort }) =>
    reasoning_effort
      ? {
          thinking: { type: 'adaptive' },
          output_config: { effort: reasoning_effort },
        }
      : {},
});
