import { Entity, PrimaryGeneratedColumn, Column, OneToMany } from 'typeorm';
import { OrderItem } from './OrderItem';
import { bigintTransformer } from '../transformers';

@Entity('orders')
export class Order {
  @PrimaryGeneratedColumn({ type: 'bigint' })
  id: number;

  @Column({ name: 'user_id', type: 'bigint', transformer: bigintTransformer })
  userId: number;

  @Column({
    name: 'order_date',
    type: 'datetime2',
    nullable: true,
    default: () => 'CURRENT_TIMESTAMP',
  })
  orderDate: Date | string | null;

  @OneToMany(() => OrderItem, (orderItem) => orderItem.order, {
    cascade: true,
    eager: true,
  })
  orderItems: OrderItem[];
}
