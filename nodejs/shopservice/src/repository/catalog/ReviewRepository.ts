import { Repository } from 'typeorm';
import { Review } from '../../entity/catalog/Review';

export class ReviewRepository {
  constructor(private repo: Repository<Review>) {}

  async save(review: Partial<Review>): Promise<Review> {
    const entity = this.repo.create(review);
    return this.repo.save(entity);
  }

  async saveAll(reviews: Partial<Review>[]): Promise<Review[]> {
    const entities = this.repo.create(reviews);
    return this.repo.save(entities);
  }

  async findById(id: number): Promise<Review | null> {
    return this.repo.findOne({
      where: { id },
      relations: ['user', 'product'],
    });
  }

  async findAll(options?: { page?: number; size?: number }): Promise<Review[]> {
    if (options && options.size !== undefined) {
      const page = options.page || 0;
      const size = options.size;
      return this.repo.find({
        skip: page * size,
        take: size,
        relations: ['user', 'product'],
        order: { id: 'ASC' },
      });
    }
    return this.repo.find({
      relations: ['user', 'product'],
      order: { id: 'ASC' },
    });
  }

  async findByUserId(
    userId: number,
    options?: { page?: number; size?: number }
  ): Promise<Review[]> {
    const findOptions: any = {
      where: { user: { id: userId } },
      relations: ['user', 'product'],
      order: { id: 'ASC' },
    };
    if (options && options.size !== undefined) {
      findOptions.skip = (options.page || 0) * options.size;
      findOptions.take = options.size;
    }
    return this.repo.find(findOptions);
  }

  async findByProductId(
    productId: number,
    options?: { page?: number; size?: number }
  ): Promise<Review[]> {
    const findOptions: any = {
      where: { product: { id: productId } },
      relations: ['user', 'product'],
      order: { id: 'ASC' },
    };
    if (options && options.size !== undefined) {
      findOptions.skip = (options.page || 0) * options.size;
      findOptions.take = options.size;
    }
    return this.repo.find(findOptions);
  }

  async count(): Promise<number> {
    return this.repo.count();
  }

  async delete(review: Review): Promise<void> {
    await this.repo.remove(review);
  }

  async deleteAll(): Promise<void> {
    await this.repo.createQueryBuilder().delete().from(Review).execute();
  }
}
