import { ValueTransformer } from 'typeorm';

export const bigintTransformer: ValueTransformer = {
  to: (value: any) => value,
  from: (value: any) => (value !== null && value !== undefined ? Number(value) : value),
};

export const decimalTransformer: ValueTransformer = {
  to: (value: any) => value,
  from: (value: any) => (value !== null && value !== undefined ? Number(value) : value),
};
