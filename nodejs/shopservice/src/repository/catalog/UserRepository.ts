import { Repository } from 'typeorm';
import { User } from '../../entity/catalog/User';

export class UserRepository {
  constructor(private repo: Repository<User>) {}

  async save(user: Partial<User>): Promise<User> {
    const entity = this.repo.create(user);
    return this.repo.save(entity);
  }

  async saveAll(users: Partial<User>[]): Promise<User[]> {
    const entities = this.repo.create(users);
    return this.repo.save(entities);
  }

  async findById(id: number): Promise<User | null> {
    return this.repo.findOneBy({ id });
  }

  async findAll(options?: { page?: number; size?: number }): Promise<User[]> {
    if (options && options.size !== undefined) {
      const page = options.page || 0;
      const size = options.size;
      return this.repo.find({
        skip: page * size,
        take: size,
        order: { id: 'ASC' },
      });
    }
    return this.repo.find({ order: { id: 'ASC' } });
  }

  async count(): Promise<number> {
    return this.repo.count();
  }

  async delete(user: User): Promise<void> {
    await this.repo.remove(user);
  }

  async deleteAll(): Promise<void> {
    await this.repo.createQueryBuilder().delete().from(User).execute();
  }
}
