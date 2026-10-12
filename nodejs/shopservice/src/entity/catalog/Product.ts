import { Entity, PrimaryGeneratedColumn, Column } from 'typeorm';
import { decimalTransformer } from '../transformers';

@Entity('products')
export class Product {
  @PrimaryGeneratedColumn({ type: 'bigint' })
  id: number;

  @Column({ type: 'varchar', length: 255 })
  name: string;

  @Column({
    type: 'decimal',
    precision: 19,
    scale: 2,
    transformer: decimalTransformer,
  })
  price: number;
}
