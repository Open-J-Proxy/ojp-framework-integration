import { Repository } from 'typeorm';
import { Product } from '../../entity/catalog/Product';

export class ProductRepository {
  constructor(private repo: Repository<Product>) {}

  async save(product: Partial<Product>): Promise<Product> {
    const entity = this.repo.create(product);
    return this.repo.save(entity);
  }

  async saveAll(products: Partial<Product>[]): Promise<Product[]> {
    const entities = this.repo.create(products);
    return this.repo.save(entities);
  }

  async findById(id: number): Promise<Product | null> {
    return this.repo.findOneBy({ id });
  }

  async findAll(options?: { page?: number; size?: number }): Promise<Product[]> {
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

  async delete(product: Product): Promise<void> {
    await this.repo.remove(product);
  }

  async deleteAll(): Promise<void> {
    await this.repo.createQueryBuilder().delete().from(Product).execute();
  }
}
