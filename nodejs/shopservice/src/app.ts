import express, { Express } from 'express';
import { DataSource } from 'typeorm';
import { User } from './entity/catalog/User';
import { Product } from './entity/catalog/Product';
import { Review } from './entity/catalog/Review';
import { Order } from './entity/checkout/Order';
import { OrderItem } from './entity/checkout/OrderItem';
import { UserRepository } from './repository/catalog/UserRepository';
import { ProductRepository } from './repository/catalog/ProductRepository';
import { ReviewRepository } from './repository/catalog/ReviewRepository';
import { OrderRepository } from './repository/checkout/OrderRepository';
import { OrderItemRepository } from './repository/checkout/OrderItemRepository';
import { createUserController } from './controller/UserController';
import { createProductController } from './controller/ProductController';
import { createReviewController } from './controller/ReviewController';
import { createOrderController } from './controller/OrderController';
import { createOrderItemController } from './controller/OrderItemController';

export interface AppContext {
  app: Express;
  catalogDataSource: DataSource;
  checkoutDataSource: DataSource;
  userRepository: UserRepository;
  productRepository: ProductRepository;
  reviewRepository: ReviewRepository;
  orderRepository: OrderRepository;
  orderItemRepository: OrderItemRepository;
}

export function createApp(
  catalogDataSource: DataSource,
  checkoutDataSource: DataSource
): AppContext {
  const app = express();
  app.use(express.json());

  const userRepository = new UserRepository(catalogDataSource.getRepository(User));
  const productRepository = new ProductRepository(catalogDataSource.getRepository(Product));
  const reviewRepository = new ReviewRepository(catalogDataSource.getRepository(Review));

  const orderRepository = new OrderRepository(checkoutDataSource.getRepository(Order));
  const orderItemRepository = new OrderItemRepository(checkoutDataSource.getRepository(OrderItem));

  app.use('/users', createUserController(userRepository));
  app.use('/products', createProductController(productRepository));
  app.use('/reviews', createReviewController(reviewRepository, userRepository, productRepository));
  app.use('/orders/:orderId/items', createOrderItemController(orderItemRepository, orderRepository, productRepository));
  app.use('/orders', createOrderController(orderRepository, userRepository, productRepository));

  return {
    app,
    catalogDataSource,
    checkoutDataSource,
    userRepository,
    productRepository,
    reviewRepository,
    orderRepository,
    orderItemRepository,
  };
}
