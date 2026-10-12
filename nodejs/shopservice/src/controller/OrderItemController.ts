import { Request, Response, Router } from 'express';
import { OrderItemRepository } from '../repository/checkout/OrderItemRepository';
import { OrderRepository } from '../repository/checkout/OrderRepository';
import { ProductRepository } from '../repository/catalog/ProductRepository';

export function createOrderItemController(
  orderItemRepository: OrderItemRepository,
  orderRepository: OrderRepository,
  productRepository: ProductRepository
): Router {
  const router = Router({ mergeParams: true });

  async function isValidOrderItem(orderItem: any): Promise<boolean> {
    if (orderItem.productId == null) return false;
    const prod = await productRepository.findById(orderItem.productId);
    return prod === null;
  }

  router.post('/', async (req: Request, res: Response) => {
    try {
      const orderId = parseInt(req.params.orderId, 10);
      const order = await orderRepository.findById(orderId);
      if (!order) {
        return res.status(400).send();
      }

      if (req.body.productId == null) {
        return res.status(400).send();
      }

      const prod = await productRepository.findById(req.body.productId);
      if (!prod) {
        return res.status(400).send();
      }

      const orderItem = req.body;
      orderItem.order = order;

      const saved = await orderItemRepository.save(orderItem);
      res.status(200).json(saved);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.get('/', async (req: Request, res: Response) => {
    try {
      const orderId = parseInt(req.params.orderId, 10);
      const items = await orderItemRepository.findByOrderId(orderId);
      res.status(200).json(items);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.get('/:itemId', async (req: Request, res: Response) => {
    try {
      const orderId = parseInt(req.params.orderId, 10);
      const itemId = parseInt(req.params.itemId, 10);
      const item = await orderItemRepository.findById(itemId);
      if (!item || !item.order || Number(item.order.id) !== orderId) {
        return res.status(404).send();
      }
      res.status(200).json(item);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.put('/:itemId', async (req: Request, res: Response) => {
    try {
      const orderId = parseInt(req.params.orderId, 10);
      const itemId = parseInt(req.params.itemId, 10);

      const invalid = await isValidOrderItem(req.body);
      if (invalid) {
        return res.status(400).send();
      }

      const item = await orderItemRepository.findById(itemId);
      if (!item || !item.order || Number(item.order.id) !== orderId) {
        return res.status(404).send();
      }

      if (req.body.productId != null && item.productId !== req.body.productId) {
        item.productId = req.body.productId;
      }
      if (req.body.quantity != null) {
        item.quantity = req.body.quantity;
      }

      const saved = await orderItemRepository.save(item);
      res.status(200).json(saved);
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  router.delete('/:itemId', async (req: Request, res: Response) => {
    try {
      const orderId = parseInt(req.params.orderId, 10);
      const itemId = parseInt(req.params.itemId, 10);

      const item = await orderItemRepository.findById(itemId);
      if (!item || !item.order || Number(item.order.id) !== orderId) {
        return res.status(404).send();
      }

      await orderItemRepository.delete(item);
      res.status(204).send();
    } catch (err) {
      res.status(500).json({ error: (err as Error).message });
    }
  });

  return router;
}
