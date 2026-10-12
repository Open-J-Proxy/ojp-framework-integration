import { Entity, PrimaryGeneratedColumn, Column } from 'typeorm';
import { bigintTransformer } from '../transformers';

@Entity('users')
export class User {
  @PrimaryGeneratedColumn({ type: 'bigint' })
  id: number;

  @Column({ type: 'varchar', length: 255 })
  username: string;

  @Column({ type: 'varchar', length: 255 })
  email: string;

  @Column({ name: 'created_at', type: 'datetime2', nullable: true })
  createdAt: Date | string | null;
}
