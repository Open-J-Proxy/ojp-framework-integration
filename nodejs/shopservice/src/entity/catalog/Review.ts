import { Entity, PrimaryGeneratedColumn, Column, ManyToOne, JoinColumn } from 'typeorm';
import { User } from './User';
import { Product } from './Product';

@Entity('reviews')
export class Review {
  @PrimaryGeneratedColumn({ type: 'bigint' })
  id: number;

  @ManyToOne(() => User, { onDelete: 'NO ACTION', eager: true })
  @JoinColumn({ name: 'user_id' })
  user: User;

  @ManyToOne(() => Product, { onDelete: 'NO ACTION', eager: true })
  @JoinColumn({ name: 'product_id' })
  product: Product;

  @Column({ type: 'int' })
  rating: number;

  @Column({ type: 'nvarchar', length: 'MAX', nullable: true })
  comment: string | null;
}
