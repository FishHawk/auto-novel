import { Glossary } from './Glossary';
import { UserReference } from './User';

export type OperationType =
  | 'web-edit'
  | 'web-edit-glossary'
  | 'wenku-edit'
  | 'wenku-edit-glossary'
  | 'wenku-upload';

export type Operation =
  | OperationWebEdit
  | OperationWebEditGlossary
  | OperationWenkuEdit
  | OperationWenkuEditGlossary
  | OperationWenkuUpload;

interface OperationWebEditData {
  titleZh: string;
  introductionZh: string;
}

export interface OperationWebEdit {
  type: 'web-edit';
  providerId: string;
  novelId: string;
  old: OperationWebEditData;
  new: OperationWebEditData;
  toc: { jp: string; old?: string; new: string }[];
}

export interface OperationWebEditGlossary {
  type: 'web-edit-glossary';
  providerId: string;
  novelId: string;
  old: Glossary;
  new: Glossary;
}

interface OperationWenkuEditData {
  title: string;
  titleZh: string;
  authors: string[];
  artists: string[];
  introduction: string;
}

export interface OperationWenkuEdit {
  type: 'wenku-edit';
  novelId: string;
  old?: OperationWenkuEditData;
  new: OperationWenkuEditData;
}

export interface OperationWenkuEditGlossary {
  type: 'wenku-edit-glossary';
  novelId: string;
  old: Glossary;
  new: Glossary;
}

export interface OperationWenkuUpload {
  type: 'wenku-upload';
  novelId: string;
  volumeId: string;
}

export interface OperationHistory {
  id: string;
  operator: UserReference;
  operation: Operation;
  createAt: number;
}

interface MergeHistoryData {
  titleJp: string;
  titleZh?: string;
  chapterId?: string;
  createAt?: number;
}

export interface MergeHistoryDto {
  id: string;
  providerId: string;
  novelId: string;
  reason: string;
  tocOld: MergeHistoryData[];
  tocNew: MergeHistoryData[];
}
